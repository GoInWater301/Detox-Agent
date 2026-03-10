#pragma once

#include <cstdint>
#include <span>
#include <string>
#include <vector>

namespace doh::util {

// ─── TC bit inspection ────────────────────────────────────────────────────────
//
// DNS header layout (RFC 1035 §4.1.1):
//   Byte 0-1: ID
//   Byte 2:   QR(1) OPCODE(4) AA(1) TC(1) RD(1)  ← TC is bit 1 of byte 2
//   Byte 3:   RA(1) Z(1) AD(1) CD(1) RCODE(4)
//
// When TC=1 on a UDP response, the caller must retry the query over TCP.
[[nodiscard]] bool is_dns_truncated(std::span<const uint8_t> message) noexcept;

// ─── QNAME extraction ─────────────────────────────────────────────────────────
//
// Extracts the first queried domain name from a DNS query wire-format message
// and returns it as a human-readable string (e.g. "example.com").
//
// Returns an empty string if the message is malformed or too short.
// Does NOT follow compression pointers in the question section (queries never
// use compression; only responses may).
[[nodiscard]] std::string dns_query_domain(std::span<const uint8_t> message) noexcept;

// ─── Block response synthesis ────────────────────────────────────────────────
//
// Builds a minimal, RFC 1035-compliant NXDOMAIN response from a DNS query.
//
// The returned message:
//   - Copies the transaction ID and question section from the query.
//   - Sets QR=1, AA=1, RD (mirrored), RCODE=3 (NXDOMAIN).
//   - Has ANCOUNT / NSCOUNT / ARCOUNT = 0.
//
// Used to respond to blocked domains without leaking the query to the upstream.
// Returns an empty vector if the query is too short to be valid.
[[nodiscard]] std::vector<uint8_t>
dns_make_nxdomain(std::span<const uint8_t> query) noexcept;

// Builds a minimal, RFC 1035-compliant REFUSED response from a DNS query.
//
// The returned message:
//   - Copies the transaction ID and question section from the query.
//   - Sets QR=1, AA=1, RD (mirrored), RCODE=5 (REFUSED).
//   - Has ANCOUNT / NSCOUNT / ARCOUNT = 0.
//
// Used to signal policy denial without claiming the domain does not exist.
// Returns an empty vector if the query is too short to be valid.
[[nodiscard]] std::vector<uint8_t>
dns_make_refused(std::span<const uint8_t> query) noexcept;

// ─── TTL override ─────────────────────────────────────────────────────────────
//
// Walks all Resource Records in a DNS *response* wire-format message and
// clamps every TTL field to [min_ttl_s, max_ttl_s].
//
// Returns the minimum TTL found after clamping, which should be used as the
// HTTP Cache-Control: max-age value.  Returns max_ttl_s if there are no RRs.
//
// Rationale: short TTLs (e.g. 30–60 s) force clients to re-query frequently,
// giving the monitoring service more data points per user session.
[[nodiscard]] uint32_t dns_clamp_response_ttl(std::vector<uint8_t>& response,
                                               uint32_t min_ttl_s,
                                               uint32_t max_ttl_s) noexcept;

} // namespace doh::util
