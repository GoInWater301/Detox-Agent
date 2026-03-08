package com.pnu.detox_agent.webserver.grpc;

import com.pnu.detox_agent.webserver.service.UsageTrackingService;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class DnsAnalyticsGrpcHandler extends DnsAnalyticsServiceGrpc.DnsAnalyticsServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(DnsAnalyticsGrpcHandler.class);

    private final UsageTrackingService usageTrackingService;

    public DnsAnalyticsGrpcHandler(UsageTrackingService usageTrackingService) {
        this.usageTrackingService = usageTrackingService;
    }

    @Override
    public StreamObserver<DnsQueryEvent> streamQueries(StreamObserver<Ack> responseObserver) {
        AtomicLong accepted = new AtomicLong();
        AtomicLong rejected = new AtomicLong();

        return new StreamObserver<>() {
            @Override
            public void onNext(DnsQueryEvent event) {
                if (event.getUserId().isBlank() || event.getQueriedDomain().isBlank()) {
                    rejected.incrementAndGet();
                    return;
                }

                long timestamp = event.getTimestampUs() > 0
                        ? event.getTimestampUs()
                        : Instant.now().toEpochMilli() * 1000L;

                try {
                    DnsQueryEvent normalized = DnsQueryEvent.newBuilder(event)
                            .setTimestampUs(timestamp)
                            .build();
                    usageTrackingService.trackUsage(normalized).block();
                    accepted.incrementAndGet();
                } catch (Exception ex) {
                    rejected.incrementAndGet();
                    log.warn("Failed to track DNS usage: user={}, domain={}", event.getUserId(), event.getQueriedDomain(), ex);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.warn("DNS stream terminated with error", throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(Ack.newBuilder()
                        .setAcceptedCount(accepted.get())
                        .setRejectedCount(rejected.get())
                        .build());
                responseObserver.onCompleted();
            }
        };
    }
}
