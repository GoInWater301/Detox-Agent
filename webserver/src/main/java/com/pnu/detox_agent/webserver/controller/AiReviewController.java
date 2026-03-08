package com.pnu.detox_agent.webserver.controller;

import com.pnu.detox_agent.webserver.dto.ai.AiReviewRequestDto;
import com.pnu.detox_agent.webserver.dto.ai.AiReviewSseEventDto;
import com.pnu.detox_agent.webserver.service.ai.AiReviewTunnelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@Tag(name = "AI Review", description = "Tunnel AI agent token stream to SSE")
public class AiReviewController {

    private final AiReviewTunnelService aiReviewTunnelService;

    public AiReviewController(AiReviewTunnelService aiReviewTunnelService) {
        this.aiReviewTunnelService = aiReviewTunnelService;
    }

    @PostMapping(value = "/api/ai/review/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream AI review tokens",
            description = "Sends usage + prompt to AI agent gRPC and streams tokens back via SSE")
    public Flux<ServerSentEvent<AiReviewSseEventDto>> streamReview(
            @RequestBody(
                            required = true,
                            content = @Content(schema = @Schema(implementation = AiReviewRequestDto.class)))
                    @org.springframework.web.bind.annotation.RequestBody
                    AiReviewRequestDto request) {
        return aiReviewTunnelService.streamReview(request)
                .map(event -> ServerSentEvent.<AiReviewSseEventDto>builder()
                        .event(event.type())
                        .data(event)
                        .build());
    }
}
