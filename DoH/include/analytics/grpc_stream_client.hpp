#pragma once

#include "analytics/analytics_client.hpp"

// Generated headers (available after cmake --build resolves the custom_command)
#include <analytics.grpc.pb.h>
#include <grpcpp/grpcpp.h>

#include <atomic>
#include <condition_variable>
#include <cstddef>
#include <deque>
#include <mutex>
#include <string>
#include <thread>

namespace doh::analytics {

// Maintains a long-lived gRPC client-side streaming RPC to AnalyticsService.
//
// Design:
//   - A single std::jthread runs the blocking gRPC writer in a dedicated loop.
//   - send() enqueues events into a bounded std::deque (protected by mutex).
//   - If the deque is full the oldest entry is dropped and a warning is logged.
//   - If the gRPC stream breaks, the worker reconnects after a 2 s backoff.
//   - The DoH path is completely unaffected by analytics failures (fault isolation).
class GrpcStreamClient : public AnalyticsClient {
public:
    GrpcStreamClient(const std::string& endpoint, std::size_t queue_cap);
    ~GrpcStreamClient() override;

    void send(QueryEvent event) noexcept override;

private:
    void worker_loop();
    bool try_stream_once();

    std::string                                           endpoint_;
    std::size_t                                           queue_cap_;
    std::shared_ptr<grpc::Channel>                        channel_;
    std::unique_ptr<::analytics::AnalyticsService::Stub>  stub_;

    std::mutex              mu_;
    std::condition_variable cv_;
    std::deque<QueryEvent>  queue_;
    std::atomic<bool>       running_{true};
    std::jthread            worker_;
};

} // namespace doh::analytics
