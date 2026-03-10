#include "util/dns_wire.hpp"

#include <algorithm>

namespace doh::util {

// ─────────────────────────────────────────────────────────────────────────────
// TC bit
// ─────────────────────────────────────────────────────────────────────────────

bool is_dns_truncated(std::span<const uint8_t> msg) noexcept {
    return msg.size() >= 4 && (msg[2] & 0x02u) != 0;
}

// ─────────────────────────────────────────────────────────────────────────────
// Block response synthesis
//
// DNS header (12 bytes):
//   Byte 0-1: Transaction ID  (copy from query)
//   Byte 2:   QR(1) OPCODE(4) AA(1) TC(0) RD(1)
//             QR=1   → response
//             OPCODE → mirror from query  (bits 3-6 of byte 2)
//             AA=1   → we are "authoritative" for the block
//             TC=0   → not truncated
//             RD     → mirror from query  (bit 0 of byte 2)
//   Byte 3:   RA(0) Z(0) AD(0) CD(0) RCODE(3)
//             RCODE   → caller-selected block response code
//   Bytes 4-5: QDCOUNT (mirror from query, usually 0x0001)
//   Bytes 6-7: ANCOUNT = 0
//   Bytes 8-9: NSCOUNT = 0
//   Bytes10-11: ARCOUNT = 0
// Question section: copied verbatim from query[12..end].
// ─────────────────────────────────────────────────────────────────────────────

namespace {

// Skip one DNS name at `pos` in `msg` (handles compression pointers).
// Returns the position *after* the name, or 0 on error.
std::size_t skip_name(std::span<const uint8_t> msg, std::size_t pos) noexcept {
    while (pos < msg.size()) {
        const uint8_t c = msg[pos];
        if (c == 0) return pos + 1;          // root label
        if ((c & 0xC0u) == 0xC0u) {          // compression pointer (2 bytes)
            if (pos + 1 >= msg.size()) return 0;
            return pos + 2;
        }
        pos += 1 + c;                         // regular label: skip length + data
    }
    return 0;  // ran off end — malformed
}

std::vector<uint8_t> dns_make_block_response(std::span<const uint8_t> query,
                                             uint8_t rcode) noexcept {
    if (query.size() < 12) return {};

    const uint16_t qdcount = (uint16_t(query[4]) << 8) | query[5];
    std::size_t question_end = 12;
    for (uint16_t i = 0; i < qdcount; ++i) {
        question_end = skip_name(query, question_end);
        if (question_end == 0 || question_end + 4 > query.size()) return {};
        question_end += 4;  // QTYPE + QCLASS
    }

    std::vector<uint8_t> resp;
    resp.reserve(question_end);

    // Transaction ID
    resp.push_back(query[0]);
    resp.push_back(query[1]);

    // Flags byte 2: QR=1, OPCODE=mirror, AA=1, TC=0, RD=mirror
    resp.push_back(static_cast<uint8_t>(0x84u | (query[2] & 0x79u)));
    // Flags byte 3: RA=0, Z=0, AD=0, CD=0, low 4 bits = RCODE
    resp.push_back(static_cast<uint8_t>(rcode & 0x0Fu));

    // QDCOUNT (mirror)
    resp.push_back(static_cast<uint8_t>((qdcount >> 8) & 0xFFu));
    resp.push_back(static_cast<uint8_t>(qdcount & 0xFFu));
    // ANCOUNT = 0
    resp.push_back(0x00); resp.push_back(0x00);
    // NSCOUNT = 0
    resp.push_back(0x00); resp.push_back(0x00);
    // ARCOUNT = 0
    resp.push_back(0x00); resp.push_back(0x00);

    // Question section only (QNAME + QTYPE + QCLASS). Do not copy EDNS or
    // other additional records from the query because the synthesized response
    // clears ANCOUNT/NSCOUNT/ARCOUNT.
    resp.insert(resp.end(), query.begin() + 12, query.begin() + question_end);

    return resp;
}

} // namespace

std::vector<uint8_t> dns_make_nxdomain(std::span<const uint8_t> query) noexcept {
    return dns_make_block_response(query, 0x03u);
}

std::vector<uint8_t> dns_make_refused(std::span<const uint8_t> query) noexcept {
    return dns_make_block_response(query, 0x05u);
}

// ─────────────────────────────────────────────────────────────────────────────
// QNAME extraction
//
// DNS QNAME wire format (RFC 1035 §3.1):
//   A sequence of length-prefixed labels terminated by a zero-length label.
//   e.g. \x07example\x03com\x00  →  "example.com"
//
// The question section starts at byte 12 (after the 12-byte fixed header).
// ─────────────────────────────────────────────────────────────────────────────

std::string dns_query_domain(std::span<const uint8_t> msg) noexcept {
    if (msg.size() < 13) return {};  // header (12) + at least one label length

    std::string domain;
    domain.reserve(64);

    std::size_t i = 12;  // skip fixed header

    while (i < msg.size()) {
        const uint8_t label_len = msg[i++];

        if (label_len == 0) break;  // root label — end of QNAME

        // Compression pointers (0xC0 prefix) are not used in questions
        if ((label_len & 0xC0u) != 0) return {};  // malformed

        if (i + label_len > msg.size()) return {};  // truncated

        if (!domain.empty()) domain += '.';
        domain.append(reinterpret_cast<const char*>(&msg[i]), label_len);
        i += label_len;
    }

    return domain;
}

// ─────────────────────────────────────────────────────────────────────────────
// TTL clamping
//
// To walk a DNS response we must navigate:
//   1. Fixed header (12 bytes) → extract RR counts
//   2. Question section (QDCOUNT records: QNAME + QTYPE(2) + QCLASS(2))
//   3. Answer   section (ANCOUNT RRs)
//   4. Authority section (NSCOUNT RRs)
//   5. Additional section (ARCOUNT RRs)
//
// Each RR layout (RFC 1035 §3.2.1):
//   NAME (variable, may be a compression pointer)
//   TYPE    (2 bytes)
//   CLASS   (2 bytes)
//   TTL     (4 bytes, big-endian)  ← we rewrite this
//   RDLENGTH(2 bytes)
//   RDATA   (RDLENGTH bytes)
// ─────────────────────────────────────────────────────────────────────────────

uint32_t dns_clamp_response_ttl(std::vector<uint8_t>& msg,
                                 uint32_t min_ttl_s,
                                 uint32_t max_ttl_s) noexcept {
    if (msg.size() < 12) return max_ttl_s;

    // Parse RR counts from header
    const uint16_t qdcount = (uint16_t(msg[4])  << 8) | msg[5];
    const uint16_t ancount = (uint16_t(msg[6])  << 8) | msg[7];
    const uint16_t nscount = (uint16_t(msg[8])  << 8) | msg[9];
    const uint16_t arcount = (uint16_t(msg[10]) << 8) | msg[11];

    std::size_t pos = 12;

    // ── Skip question section ──────────────────────────────────────────────
    for (uint16_t i = 0; i < qdcount; ++i) {
        pos = skip_name(msg, pos);
        if (pos == 0 || pos + 4 > msg.size()) return max_ttl_s;
        pos += 4;  // QTYPE + QCLASS
    }

    // ── Walk all RR sections and rewrite TTL ──────────────────────────────
    uint32_t min_found = max_ttl_s;
    const uint16_t total_rr = ancount + nscount + arcount;

    for (uint16_t i = 0; i < total_rr; ++i) {
        pos = skip_name(msg, pos);
        if (pos == 0 || pos + 10 > msg.size()) break;

        pos += 4;  // TYPE(2) + CLASS(2)

        // Read current TTL
        uint32_t ttl = (uint32_t(msg[pos])   << 24)
                     | (uint32_t(msg[pos+1]) << 16)
                     | (uint32_t(msg[pos+2]) <<  8)
                     |  uint32_t(msg[pos+3]);

        // Clamp to [min, max]
        ttl = std::max(ttl, min_ttl_s);
        ttl = std::min(ttl, max_ttl_s);

        msg[pos]   = static_cast<uint8_t>((ttl >> 24) & 0xFFu);
        msg[pos+1] = static_cast<uint8_t>((ttl >> 16) & 0xFFu);
        msg[pos+2] = static_cast<uint8_t>((ttl >>  8) & 0xFFu);
        msg[pos+3] = static_cast<uint8_t>( ttl        & 0xFFu);

        min_found = std::min(min_found, ttl);
        pos += 4;  // past TTL field

        // Read RDLENGTH and skip RDATA
        if (pos + 2 > msg.size()) break;
        const uint16_t rdlen = (uint16_t(msg[pos]) << 8) | msg[pos+1];
        pos += 2 + rdlen;
    }

    return min_found;
}

} // namespace doh::util
