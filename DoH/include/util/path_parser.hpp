#pragma once

#include <optional>
#include <string_view>

namespace doh::util {

// Parses the DoH request target: /{user_id}/dns-query[?...]
//
// Returns a zero-copy string_view into `target` pointing at the user_id
// segment. Returns std::nullopt if the path is malformed or missing the
// required "/dns-query" suffix.
[[nodiscard]] std::optional<std::string_view>
parse_user_id(std::string_view target) noexcept;

} // namespace doh::util
