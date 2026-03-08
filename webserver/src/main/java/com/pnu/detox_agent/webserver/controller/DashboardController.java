package com.pnu.detox_agent.webserver.controller;

import com.pnu.detox_agent.webserver.dto.DomainUsageDto;
import com.pnu.detox_agent.webserver.dto.TimelineStatsDto;
import com.pnu.detox_agent.webserver.dto.UsageStatsDto;
import com.pnu.detox_agent.webserver.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "DNS analytics dashboard endpoints")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/users/{userId}/usage")
    @Operation(summary = "Get usage summary", description = "Returns total queries, unique domains and top domains for a user")
    public Mono<ResponseEntity<UsageStatsDto>> getUserUsage(
            @PathVariable String userId,
            @Parameter(description = "daily, weekly, monthly") @RequestParam(defaultValue = "daily") String period) {

        return dashboardService.getUserUsageStats(userId, period)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/users/{userId}/domains")
    @Operation(summary = "Get domain usage list", description = "Returns domain-level usage records, optionally filtered by date range")
    public Flux<DomainUsageDto> getUserDomainUsage(
            @PathVariable String userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        return dashboardService.getUserDomainUsage(userId, startDate, endDate);
    }

    @GetMapping("/users/{userId}/timeline")
    @Operation(summary = "Get timeline stats", description = "Returns timeline buckets for selected period")
    public Flux<TimelineStatsDto> getUserTimeline(
            @PathVariable String userId,
            @Parameter(description = "daily, weekly, monthly") @RequestParam(defaultValue = "daily") String period) {

        return dashboardService.getUserTimeline(userId, period);
    }
}
