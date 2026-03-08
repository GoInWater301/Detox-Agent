package com.pnu.detox_agent.webserver.service.ai;

import com.pnu.detox_agent.webserver.grpc.AiAgentReviewServiceGrpc;
import com.pnu.detox_agent.webserver.grpc.ReviewRequest;
import com.pnu.detox_agent.webserver.grpc.ReviewToken;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class AiAgentGrpcClient {

    private final ManagedChannel managedChannel;
    private final AiAgentReviewServiceGrpc.AiAgentReviewServiceStub reviewStub;

    public AiAgentGrpcClient(
            @Value("${ai.agent.grpc.host:localhost}") String host,
            @Value("${ai.agent.grpc.port:50051}") int port,
            @Value("${ai.agent.grpc.use-plaintext:true}") boolean usePlaintext) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
        if (usePlaintext) {
            builder.usePlaintext();
        }
        this.managedChannel = builder.build();
        this.reviewStub = AiAgentReviewServiceGrpc.newStub(managedChannel);
    }

    public Flux<ReviewToken> streamReview(ReviewRequest request) {
        Sinks.Many<ReviewToken> sink = Sinks.many().unicast().onBackpressureBuffer();

        reviewStub.streamReview(request, new StreamObserver<>() {
            @Override
            public void onNext(ReviewToken token) {
                sink.tryEmitNext(token);
            }

            @Override
            public void onError(Throwable throwable) {
                sink.tryEmitError(throwable);
            }

            @Override
            public void onCompleted() {
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux();
    }

    @PreDestroy
    public void shutdown() {
        managedChannel.shutdown();
    }
}
