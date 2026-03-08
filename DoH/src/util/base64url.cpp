#include "util/base64url.hpp"

namespace doh::util {

namespace {

constexpr int decode_char(char c) noexcept {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '-' || c == '+') return 62;  // base64url '-' or standard '+'
    if (c == '_' || c == '/') return 63;  // base64url '_' or standard '/'
    return -1;                             // invalid (includes '=')
}

} // namespace

std::optional<std::vector<uint8_t>> base64url_decode(std::string_view input) {
    std::vector<uint8_t> out;
    out.reserve((input.size() * 3) / 4 + 1);

    uint32_t buf  = 0;
    int      bits = 0;

    for (const char c : input) {
        if (c == '=') break;      // padding — stop gracefully (RFC 8484: no padding)
        const int v = decode_char(c);
        if (v < 0) return std::nullopt;

        buf   = (buf << 6) | static_cast<uint32_t>(v);
        bits += 6;

        if (bits >= 8) {
            bits -= 8;
            out.push_back(static_cast<uint8_t>(buf >> bits));
            buf &= (1u << bits) - 1u;
        }
    }

    return out;
}

} // namespace doh::util
