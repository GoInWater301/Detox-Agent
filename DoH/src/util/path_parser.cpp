#include "util/path_parser.hpp"

namespace doh::util {

std::optional<std::string_view> parse_user_id(std::string_view target) noexcept {
    // Strip query string first
    const auto qpos = target.find('?');
    const auto path = target.substr(0, qpos);

    // Must start with '/'
    if (path.empty() || path.front() != '/') return std::nullopt;

    std::string_view rest = path.substr(1);  // drop leading '/'

    // Split at next '/' to get user_id and suffix
    const auto sep = rest.find('/');
    if (sep == std::string_view::npos) return std::nullopt;

    const auto user_id = rest.substr(0, sep);
    const auto suffix  = rest.substr(sep + 1);

    if (user_id.empty())         return std::nullopt;
    if (suffix != "dns-query")   return std::nullopt;

    return user_id;
}

} // namespace doh::util
