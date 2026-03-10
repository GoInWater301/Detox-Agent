#include "filter/redis_domain_filter.hpp"

#include <boost/redis/src.hpp>
#include <spdlog/spdlog.h>

#include <algorithm>
#include <charconv>
#include <mutex>
#include <string_view>

namespace doh::filter {

namespace net   = boost::asio;
namespace redis = boost::redis;

namespace {

static constexpr std::string_view kSnapshotScript = R"lua(
local result = {}

local function push(value)
    table.insert(result, string.lower(tostring(value)))
end

local global_members = redis.call("SMEMBERS", "doh:block:global")
push("__global__")
push(#global_members)
for _, member in ipairs(global_members) do
    push(member)
end

local cursor = "0"
repeat
    local scan = redis.call("SCAN", cursor, "MATCH", "doh:block:*", "COUNT", "512")
    cursor = scan[1]
    local keys = scan[2]

    for _, key in ipairs(keys) do
        if key ~= "doh:block:global" then
            local user_id = string.sub(key, 11)
            local members = redis.call("SMEMBERS", key)
            push("__user__")
            push(user_id)
            push(#members)
            for _, member in ipairs(members) do
                push(member)
            end
        end
    end
until cursor == "0"

return result
)lua";

std::string normalize(std::string_view value) {
    std::string out(value);
    std::transform(out.begin(), out.end(), out.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return out;
}

bool parse_count(std::string_view value, std::size_t& out) {
    const char* begin = value.data();
    const char* end = begin + value.size();
    auto [ptr, ec] = std::from_chars(begin, end, out);
    return ec == std::errc{} && ptr == end;
}

template <class SetT>
bool match_suffix(const SetT& domains, std::string_view fqdn) {
    std::size_t start = 0;
    while (start < fqdn.size()) {
        if (domains.contains(std::string(fqdn.substr(start)))) {
            return true;
        }

        const auto dot = fqdn.find('.', start);
        if (dot == std::string_view::npos) {
            break;
        }
        start = dot + 1;
    }
    return false;
}

bool parse_snapshot_nodes(const std::vector<redis::resp3::node>& nodes,
                          RedisDomainFilter::Snapshot& snapshot) {
    if (nodes.empty() || nodes.front().data_type != redis::resp3::type::array) {
        return false;
    }

    std::vector<std::string_view> values;
    values.reserve(nodes.front().aggregate_size);
    for (std::size_t i = 1; i < nodes.size(); ++i) {
        if (nodes[i].depth == 1) {
            values.emplace_back(nodes[i].value);
        }
    }

    std::size_t idx = 0;
    if (idx >= values.size() || values[idx++] != "__global__") {
        return false;
    }

    std::size_t global_count = 0;
    if (idx >= values.size() || !parse_count(values[idx++], global_count)) {
        return false;
    }
    for (std::size_t i = 0; i < global_count; ++i) {
        if (idx >= values.size()) return false;
        snapshot.global.emplace(values[idx++]);
    }

    while (idx < values.size()) {
        if (values[idx++] != "__user__") {
            return false;
        }
        if (idx >= values.size()) return false;
        const std::string user(values[idx++]);

        std::size_t member_count = 0;
        if (idx >= values.size() || !parse_count(values[idx++], member_count)) {
            return false;
        }

        auto& set = snapshot.per_user[user];
        for (std::size_t i = 0; i < member_count; ++i) {
            if (idx >= values.size()) return false;
            set.emplace(values[idx++]);
        }
    }

    return true;
}

} // namespace

RedisDomainFilter::RedisDomainFilter(net::io_context& ioc,
                                      std::string      host,
                                      uint16_t         port,
                                      std::string      password,
                                      uint32_t         timeout_ms,
                                      uint32_t         refresh_ms,
                                      bool             fail_open)
    : refresh_timer_(ioc)
    , timeout_(timeout_ms)
    , refresh_interval_(refresh_ms)
    , fail_open_(fail_open)
{
    redis_cfg_.addr.host = host;
    redis_cfg_.addr.port = std::to_string(port);
    if (!password.empty()) redis_cfg_.password = std::move(password);

    spdlog::info("Redis domain filter → {}:{} (timeout={} ms, refresh={} ms, mode={})",
                 host, port, timeout_.count(), refresh_interval_.count(),
                 fail_open_ ? "fail-open" : "fail-closed");

    schedule_refresh(std::chrono::milliseconds{0});
}

void RedisDomainFilter::async_check(std::string                  user_id,
                                     std::string                  domain,
                                     net::any_io_executor         ex,
                                     FilterCallback               cb)
{
    if (domain.empty()) {
        net::dispatch(ex, [cb = std::move(cb)]() mutable { cb(false); });
        return;
    }

    const std::string normalized_domain = normalize(domain);
    const std::string normalized_user = normalize(user_id);

    bool blocked = false;
    {
        std::shared_lock lock(snapshot_mu_);
        if (!ready_) {
            blocked = !fail_open_;
        } else {
            blocked = match_suffix(snapshot_.global, normalized_domain);
            if (!blocked) {
                if (const auto it = snapshot_.per_user.find(normalized_user);
                    it != snapshot_.per_user.end()) {
                    blocked = match_suffix(it->second, normalized_domain);
                }
            }
        }
    }

    net::dispatch(ex, [cb = std::move(cb), blocked]() mutable { cb(blocked); });
}

void RedisDomainFilter::schedule_refresh(std::chrono::milliseconds delay) {
    refresh_timer_.expires_after(delay);
    refresh_timer_.async_wait([this](boost::system::error_code ec) {
        if (ec == net::error::operation_aborted) {
            return;
        }
        if (ec) {
            spdlog::warn("Filter refresh timer error: {}", ec.message());
            schedule_refresh(refresh_interval_);
            return;
        }
        refresh_snapshot();
    });
}

void RedisDomainFilter::refresh_snapshot() {
    auto conn = std::make_shared<redis::connection>(refresh_timer_.get_executor());
    auto req = std::make_shared<redis::request>();
    auto resp = std::make_shared<redis::generic_response>();

    req->push("EVAL", kSnapshotScript, "0");

    conn->async_run(redis_cfg_, net::consign(net::detached, conn));

    conn->async_exec(
        *req,
        *resp,
        net::cancel_after(
            timeout_,
            [this, conn, req, resp, timeout = timeout_](boost::system::error_code ec, std::size_t) {
                conn->cancel();
                if (ec) {
                    if (ec == net::error::operation_aborted) {
                        spdlog::warn("Filter snapshot refresh timed out after {} ms",
                                     timeout.count());
                    } else {
                        spdlog::warn("Filter snapshot refresh error: {}", ec.message());
                    }
                    schedule_refresh(refresh_interval_);
                    return;
                }

                Snapshot next;
                if (!resp->has_value() || !parse_snapshot_nodes(resp->value(), next)) {
                    spdlog::warn("Filter snapshot refresh returned malformed data");
                    schedule_refresh(refresh_interval_);
                    return;
                }

                std::size_t user_count = 0;
                std::size_t domain_count = next.global.size();
                for (const auto& [_, domains] : next.per_user) {
                    ++user_count;
                    domain_count += domains.size();
                }

                {
                    std::unique_lock lock(snapshot_mu_);
                    snapshot_ = std::move(next);
                    ready_ = true;
                }

                spdlog::info("Filter snapshot refreshed: users={} domains={}",
                             user_count, domain_count);
                schedule_refresh(refresh_interval_);
            }));
}

} // namespace doh::filter
