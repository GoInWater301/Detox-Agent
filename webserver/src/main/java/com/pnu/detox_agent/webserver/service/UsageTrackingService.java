package com.pnu.detox_agent.webserver.service;

import com.pnu.detox_agent.webserver.entity.DomainUsageRecord;
import com.pnu.detox_agent.webserver.grpc.DnsQueryEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UsageTrackingService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ReactiveStringRedisTemplate redisTemplate;
    private final UsagePersistenceService usagePersistenceService;

    public UsageTrackingService(
            ReactiveStringRedisTemplate redisTemplate,
            UsagePersistenceService usagePersistenceService) {
        this.redisTemplate = redisTemplate;
        this.usagePersistenceService = usagePersistenceService;
    }

    public Mono<DomainUsageRecord> trackUsage(DnsQueryEvent event) {
        String usageKey = usageKey(event.getUserId(), event.getQueriedDomain());
        long timestampUs = event.getTimestampUs() > 0
                ? event.getTimestampUs()
                : Instant.now().toEpochMilli() * 1000L;
        long responseTimeMs = Integer.toUnsignedLong(event.getLatencyUs()) / 1000L;

        return redisTemplate.<String, String>opsForHash()
                .entries(usageKey)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .defaultIfEmpty(Map.of())
                .flatMap(current -> {
                    long firstAccess = parseLong(current.get("firstAccess"), timestampUs);
                    long lastAccess = parseLong(current.get("lastAccess"), timestampUs);
                    long count = parseLong(current.get("count"), 0L) + 1;
                    long totalDuration = parseLong(current.get("totalDuration"), 0L);
                    if (!current.isEmpty() && timestampUs > lastAccess) {
                        totalDuration += (timestampUs - lastAccess);
                    }
                    long responseSum = parseLong(current.get("responseTimeSum"), 0L) + responseTimeMs;
                    long avgResponse = count == 0 ? 0L : responseSum / count;

                    Map<String, String> updates = Map.of(
                            "userId", event.getUserId(),
                            "domain", event.getQueriedDomain(),
                            "firstAccess", String.valueOf(firstAccess),
                            "lastAccess", String.valueOf(timestampUs),
                            "count", String.valueOf(count),
                            "totalDuration", String.valueOf(totalDuration),
                            "responseTimeSum", String.valueOf(responseSum),
                            "avgResponseTimeMs", String.valueOf(avgResponse));

                    return redisTemplate.<String, String>opsForHash().putAll(usageKey, updates)
                            .then(indexDomain(event.getUserId(), event.getQueriedDomain()))
                            .then(incrementPeriodStats(event.getUserId(), event.getQueriedDomain(), timestampUs))
                            .then(usagePersistenceService.persistEvent(
                                    event.getUserId(),
                                    event.getQueriedDomain(),
                                    timestampUs,
                                    responseTimeMs))
                            .thenReturn(new DomainUsageRecord(
                                    event.getUserId(),
                                    event.getQueriedDomain(),
                                    firstAccess,
                                    timestampUs,
                                    count,
                                    totalDuration,
                                    avgResponse));
                });
    }

    private Mono<Long> incrementPeriodStats(String userId, String domain, long timestampUs) {
        Instant instant = Instant.ofEpochMilli(timestampUs / 1000L);
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();

        String day = date.toString();
        WeekFields weekFields = WeekFields.of(Locale.ROOT);
        int week = date.get(weekFields.weekOfWeekBasedYear());
        int weekYear = date.get(weekFields.weekBasedYear());
        String weekId = weekYear + "-W" + String.format("%02d", week);
        String monthId = date.format(MONTH_FORMAT);

        return Mono.when(
                        incrementCounter(statsKey("daily", userId, day), domain),
                        incrementCounter(statsKey("weekly", userId, weekId), domain),
                        incrementCounter(statsKey("monthly", userId, monthId), domain))
                .thenReturn(1L);
    }

    private Mono<Boolean> incrementCounter(String key, String domain) {
        return redisTemplate.opsForHash().increment(key, "totalQueries", 1)
                .then(redisTemplate.opsForHash().increment(key, "domain:" + domain, 1))
                .thenReturn(true);
    }

    private Mono<Long> indexDomain(String userId, String domain) {
        ReactiveSetOperations<String, String> setOps = redisTemplate.opsForSet();
        return setOps.add(userDomainsKey(userId), domain);
    }

    public static String usageKey(String userId, String domain) {
        return "usage:" + userId + ":" + domain;
    }

    public static String userDomainsKey(String userId) {
        return "usage:index:user:" + userId;
    }

    public static String statsKey(String period, String userId, String bucket) {
        return "stats:" + period + ":" + userId + ":" + bucket;
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
