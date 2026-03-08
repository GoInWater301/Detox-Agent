#pragma once

#include <cstdint>
#include <optional>
#include <string_view>
#include <vector>

namespace doh::util {

// RFC 4648 §5 base64url decode.
// Accepts both base64url ('-', '_') and standard base64 ('+', '/').
// Padding ('=') terminates decoding gracefully; no error.
// Returns std::nullopt on any invalid character.
[[nodiscard]] std::optional<std::vector<uint8_t>>
base64url_decode(std::string_view input);

} // namespace doh::util
