package com.pnu.detox_agent.webserver.analytics;

import com.pnu.detox_agent.webserver.service.UsagePersistenceService;
import com.pnu.detox_agent.webserver.service.UsageTrackingService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class StatisticsAggregator {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ReactiveStringRedisTemplate redisTemplate;
    private final UsagePersistenceService usagePersistenceService;

    public StatisticsAggregator(
            ReactiveStringRedisTemplate redisTemplate,
            UsagePersistenceService usagePersistenceService) {
        this.redisTemplate = redisTemplate;
        this.usagePersistenceService = usagePersistenceService;
    }

    @Scheduled(fixedDelay = 60000)
    public void aggregateRealTimeStats() {
        redisTemplate.keys("usage:index:user:*")
                .map(key -> key.substring("usage:index:user:".length()))
                .flatMap(this::aggregateUserStats)
                .onErrorResume(ex -> Mono.empty())
                .subscribe();
    }

    private Mono<Void> aggregateUserStats(String userId) {
        return Mono.when(
                persistPeriodSnapshot(userId, "daily", currentBucket("daily")),
                persistPeriodSnapshot(userId, "weekly", currentBucket("weekly")),
                persistPeriodSnapshot(userId, "monthly", currentBucket("monthly")));
    }

    private Mono<Void> persistPeriodSnapshot(String userId, String period, String bucket) {
        String key = UsageTrackingService.statsKey(period, userId, bucket);
        return redisTemplate.<String, String>opsForHash().entries(key)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(values -> {
                    long total = parseLong(values.get("totalQueries"), 0L);
                    long unique = values.keySet().stream().filter(v -> v.startsWith("domain:")).count();
                    return usagePersistenceService.persistSnapshot(userId, period, bucket, total, unique);
                })
                .then();
    }

    private String currentBucket(String period) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return switch (period) {
            case "weekly" -> {
                WeekFields weekFields = WeekFields.of(Locale.ROOT);
                int week = now.get(weekFields.weekOfWeekBasedYear());
                int year = now.get(weekFields.weekBasedYear());
                yield year + "-W" + String.format("%02d", week);
            }
            case "monthly" -> now.format(MONTH_FORMAT);
            default -> now.toString();
        };
    }

    private long parseLong(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
