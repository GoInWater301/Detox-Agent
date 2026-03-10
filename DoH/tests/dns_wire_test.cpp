#include "util/dns_wire.hpp"

#include <cassert>
#include <cstdint>
#include <vector>

namespace {

std::vector<uint8_t> minimal_query() {
    return {
        0xaa, 0xbb, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x07, 'e',  'x',  'a',  'm',  'p',  'l',  'e',
        0x03, 'c',  'o',  'm',  0x00,
        0x00, 0x01, 0x00, 0x01
    };
}

std::vector<uint8_t> edns_query() {
    return {
        0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x01,
        0x07, 'b',  'l',  'o',  'c',  'k',  'e',  'd',
        0x03, 'c',  'o',  'm',  0x00,
        0x00, 0x01, 0x00, 0x01,
        0x00,
        0x00, 0x29,
        0x10, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00
    };
}

void expect_common_header(const std::vector<uint8_t>& response,
                          const std::vector<uint8_t>& query,
                          uint8_t expected_rcode) {
    assert(response.size() >= 12);
    assert(response[0] == query[0]);
    assert(response[1] == query[1]);
    assert((response[2] & 0x80u) != 0);         // QR=1
    assert((response[2] & 0x04u) != 0);         // AA=1
    assert((response[2] & 0x01u) == (query[2] & 0x01u));  // RD mirrored
    assert((response[3] & 0x0Fu) == expected_rcode);
    assert(response[4] == query[4] && response[5] == query[5]);  // QDCOUNT
    assert(response[6] == 0x00 && response[7] == 0x00);          // ANCOUNT
    assert(response[8] == 0x00 && response[9] == 0x00);          // NSCOUNT
    assert(response[10] == 0x00 && response[11] == 0x00);        // ARCOUNT
}

}  // namespace

int main() {
    {
        const auto query = minimal_query();
        const auto response = doh::util::dns_make_refused(query);

        expect_common_header(response, query, 0x05u);
        assert(response.size() == query.size());
    }

    {
        const auto query = edns_query();
        const auto response = doh::util::dns_make_refused(query);

        expect_common_header(response, query, 0x05u);
        assert(response.size() == 29);  // header + question only
    }

    {
        const auto query = minimal_query();
        const auto response = doh::util::dns_make_nxdomain(query);

        expect_common_header(response, query, 0x03u);
        assert(response.size() == query.size());
    }

    return 0;
}
