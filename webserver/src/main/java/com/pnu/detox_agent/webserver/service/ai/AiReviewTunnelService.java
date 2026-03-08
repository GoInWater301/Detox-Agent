package com.pnu.detox_agent.webserver.service.ai;

import com.pnu.detox_agent.webserver.dto.DomainUsageDto;
import com.pnu.detox_agent.webserver.dto.UsageStatsDto;
import com.pnu.detox_agent.webserver.dto.ai.AiReviewRequestDto;
import com.pnu.detox_agent.webserver.dto.ai.AiReviewSseEventDto;
import com.pnu.detox_agent.webserver.grpc.DomainUsage;
import com.pnu.detox_agent.webserver.grpc.ReviewRequest;
import com.pnu.detox_agent.webserver.grpc.ReviewToken;
import com.pnu.detox_agent.webserver.grpc.UsageDto;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AiReviewTunnelService {

    private final AiAgentGrpcClient aiAgentGrpcClient;

    public AiReviewTunnelService(AiAgentGrpcClient aiAgentGrpcClient) {
        this.aiAgentGrpcClient = aiAgentGrpcClient;
    }

    public Flux<AiReviewSseEventDto> streamReview(AiReviewRequestDto requestDto) {
        ReviewRequest request = toGrpcRequest(requestDto);

        return aiAgentGrpcClient.streamReview(request)
                .map(this::toSseEvent)
                .concatWith(Flux.just(new AiReviewSseEventDto("done", "", true, "", "", "")))
                .onErrorResume(ex -> Flux.just(new AiReviewSseEventDto(
                        "error",
                        "",
                        true,
                        "",
                        "",
                        ex.getMessage() == null ? "AI review stream failed" : ex.getMessage())));
    }

    private AiReviewSseEventDto toSseEvent(ReviewToken token) {
        String eventType = token.getDone() ? "done" : "token";
        return new AiReviewSseEventDto(
                eventType,
                token.getToken(),
                token.getDone(),
                token.getModel(),
                token.getMessageId(),
                "");
    }

    private ReviewRequest toGrpcRequest(AiReviewRequestDto requestDto) {
        UsageDto usage = toGrpcUsage(requestDto.usage());
        return ReviewRequest.newBuilder()
                .setSessionId(nullToEmpty(requestDto.sessionId()))
                .setPrompt(nullToEmpty(requestDto.prompt()))
                .setUsage(usage)
                .build();
    }

    private UsageDto toGrpcUsage(UsageStatsDto usage) {
        if (usage == null) {
            return UsageDto.getDefaultInstance();
        }

        List<DomainUsage> domains = usage.topDomains() == null
                ? List.of()
                : usage.topDomains().stream().filter(Objects::nonNull).map(this::toGrpcDomainUsage).toList();

        return UsageDto.newBuilder()
                .setUserId(nullToEmpty(usage.userId()))
                .setPeriod(nullToEmpty(usage.period()))
                .setTotalQueries(usage.totalQueries())
                .setUniqueDomains(usage.uniqueDomains())
                .addAllTopDomains(domains)
                .build();
    }

    private DomainUsage toGrpcDomainUsage(DomainUsageDto dto) {
        return DomainUsage.newBuilder()
                .setDomain(nullToEmpty(dto.domain()))
                .setRequestCount(dto.requestCount())
                .setFirstAccess(dto.firstAccess())
                .setLastAccess(dto.lastAccess())
                .setTotalDuration(dto.totalDuration())
                .setAverageResponseTimeMs(dto.averageResponseTimeMs())
                .build();
    }

    private String nullToEmpty(String input) {
        return input == null ? "" : input;
    }
}
