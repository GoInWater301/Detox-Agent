#include "analytics/grpc_stream_client.hpp"

#include <spdlog/spdlog.h>
#include <chrono>

namespace doh::analytics {

GrpcStreamClient::GrpcStreamClient(const std::string& endpoint, std::size_t queue_cap)
    : endpoint_ (endpoint)
    , queue_cap_(queue_cap)
    , channel_  (grpc::CreateChannel(endpoint, grpc::InsecureChannelCredentials()))
    , stub_     (::analytics::AnalyticsService::NewStub(channel_))
    , worker_   ([this] { worker_loop(); })
{
    spdlog::info("Analytics gRPC client → {}", endpoint);
}

GrpcStreamClient::~GrpcStreamClient() {
    running_ = false;
    cv_.notify_all();
    // std::jthread destructor calls request_stop() then join()
}

// ─────────────────────────────────────────────────────────────────────────────
// Public API (called from io_context threads)
// ─────────────────────────────────────────────────────────────────────────────

void GrpcStreamClient::send(QueryEvent event) noexcept {
    std::lock_guard lock(mu_);
    if (queue_.size() >= queue_cap_) {
        // Bounded queue: drop the oldest event to protect memory
        spdlog::warn("Analytics queue full — dropping event for user={}",
                     queue_.front().user_id);
        queue_.pop_front();
    }
    queue_.push_back(std::move(event));
    cv_.notify_one();
}

// ─────────────────────────────────────────────────────────────────────────────
// Worker thread
// ─────────────────────────────────────────────────────────────────────────────

void GrpcStreamClient::worker_loop() {
    while (running_.load(std::memory_order_relaxed)) {
        if (!try_stream_once() && running_) {
            spdlog::warn("Analytics stream closed — reconnecting in 2 s...");
            std::this_thread::sleep_for(std::chrono::seconds(2));
        }
    }
}

bool GrpcStreamClient::try_stream_once() {
    grpc::ClientContext  ctx;
    ::analytics::Ack     ack;
    auto writer = stub_->StreamQueries(&ctx, &ack);

    while (running_.load(std::memory_order_relaxed)) {
        std::unique_lock lock(mu_);
        // Wait for events or shutdown signal (poll every 200 ms to catch shutdown)
        cv_.wait_for(lock, std::chrono::milliseconds(200),
                     [this] { return !queue_.empty() || !running_; });

        while (!queue_.empty()) {
            auto ev = std::move(queue_.front());
            queue_.pop_front();
            lock.unlock();

            ::analytics::DnsQueryEvent msg;
            msg.set_user_id        (ev.user_id);
            msg.set_queried_domain (ev.queried_domain);
            msg.set_client_ip      (ev.client_ip);
            msg.set_raw_query      (ev.raw_query.data(),    ev.raw_query.size());
            msg.set_raw_response   (ev.raw_response.data(), ev.raw_response.size());
            msg.set_timestamp_us   (ev.timestamp_us);
            msg.set_latency_us     (ev.latency_us);
            msg.set_used_tcp       (ev.used_tcp);

            if (!writer->Write(msg)) {
                spdlog::error("Analytics: gRPC Write() failed — scheduling reconnect");
                writer->WritesDone();
                writer->Finish();
                return false;
            }

            lock.lock();
        }
    }

    writer->WritesDone();
    const auto status = writer->Finish();
    if (!status.ok()) {
        spdlog::warn("Analytics: stream finished with error: {}",
                     status.error_message());
        return false;
    }
    return true;
}

} // namespace doh::analytics
