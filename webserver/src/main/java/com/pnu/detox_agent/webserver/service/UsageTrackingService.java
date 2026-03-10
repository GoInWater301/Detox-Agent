package com.pnu.detox_agent.webserver.service;

import com.pnu.detox_agent.webserver.entity.DomainUsageRecord;
import com.pnu.detox_agent.webserver.grpc.DnsQueryEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UsageTrackingService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final long SESSION_GAP_US = 180_000_000L;
    private static final long STREAMING_SESSION_GAP_US = 300_000_000L;
    private static final Duration LAST_QUERY_TTL = Duration.ofSeconds(390);
    private static final ZoneId ANALYTICS_ZONE = ZoneId.of("Asia/Seoul");

    private final ReactiveStringRedisTemplate redisTemplate;
    private final UsagePersistenceService usagePersistenceService;

    public UsageTrackingService(
            ReactiveStringRedisTemplate redisTemplate,
            UsagePersistenceService usagePersistenceService) {
        this.redisTemplate = redisTemplate;
        this.usagePersistenceService = usagePersistenceService;
    }

    public Mono<DomainUsageRecord> trackUsage(DnsQueryEvent event) {
        long timestampUs = event.getTimestampUs() > 0
                ? event.getTimestampUs()
                : Instant.now().toEpochMilli() * 1000L;
        long responseTimeMs = Integer.toUnsignedLong(event.getLatencyUs()) / 1000L;
        String rawDomain = DomainAggregationPolicy.normalizeHost(event.getQueriedDomain());
        Optional<String> displayDomain = DomainAggregationPolicy.toDisplayDomain(rawDomain);

        Mono<Void> persistRawEvent = usagePersistenceService.persistEvent(
                event.getUserId(),
                rawDomain,
                timestampUs,
                responseTimeMs);

        return displayDomain
                .map(domain -> readLastQuery(event.getUserId(), domain)
                        .flatMap(lastQuery -> applySessionDuration(lastQuery, event.getUserId(), timestampUs)
                                .then(updateCurrentUsage(event.getUserId(), domain, timestampUs, responseTimeMs))
                                .flatMap(record -> writeLastQuery(event.getUserId(), lastQuery, domain, timestampUs)
                                        .then(incrementPeriodStats(event.getUserId(), domain, timestampUs))
                                        .then(persistRawEvent)
                                        .thenReturn(record))))
                .orElseGet(() -> persistRawEvent.thenReturn(new DomainUsageRecord(
                        event.getUserId(),
                        rawDomain,
                        timestampUs,
                        timestampUs,
                        0L,
                        0L,
                        responseTimeMs)));
    }

    private Mono<DomainUsageRecord> updateCurrentUsage(
            String userId,
            String domain,
            long timestampUs,
            long responseTimeMs) {
        String date = dateFromTimestamp(timestampUs);
        String usageKey = usageKey(userId, domain, date);

        return redisTemplate.<String, String>opsForHash()
                .entries(usageKey)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .defaultIfEmpty(Map.of())
                .flatMap(current -> {
                    if (current.isEmpty()) {
                        return Mono.just(new DomainUsageRecord(
                                userId,
                                domain,
                                timestampUs,
                                timestampUs,
                                0L,
                                0L,
                                responseTimeMs));
                    }

                    long firstAccess = parseLong(current.get("firstAccess"), timestampUs);
                    long count = parseLong(current.get("count"), 0L) + 1;
                    long totalDuration = parseLong(current.get("totalDuration"), 0L);
                    long responseSum = parseLong(current.get("responseTimeSum"), 0L) + responseTimeMs;
                    long avgResponse = count == 0 ? 0L : responseSum / count;

                    Map<String, String> updates = Map.of(
                            "userId", userId,
                            "domain", domain,
                            "firstAccess", String.valueOf(firstAccess),
                            "lastAccess", String.valueOf(timestampUs),
                            "count", String.valueOf(count),
                            "totalDuration", String.valueOf(totalDuration),
                            "responseTimeSum", String.valueOf(responseSum),
                            "avgResponseTimeMs", String.valueOf(avgResponse));

                    return redisTemplate.<String, String>opsForHash().putAll(usageKey, updates)
                            .then(indexDomain(userId, domain, date))
                            .thenReturn(new DomainUsageRecord(
                                    userId,
                                    domain,
                                    firstAccess,
                                    timestampUs,
                                    count,
                                    totalDuration,
                                    avgResponse));
                });
    }

    private Mono<Void> applySessionDuration(
            LastQueryCache lastQuery,
            String userId,
            long currentTimestampUs) {
        if (lastQuery == null || lastQuery.domain().isBlank()) {
            return Mono.empty();
        }

        long gapUs = currentTimestampUs - lastQuery.timestampUs();
        if (gapUs <= 0 || gapUs > sessionGapUs(lastQuery.domain())) {
            return Mono.empty();
        }

        long sessionStartUs = lastQuery.sessionStartUs() > 0 ? lastQuery.sessionStartUs() : lastQuery.timestampUs();
        long totalSessionMinutes = Math.max(0L, (currentTimestampUs - sessionStartUs) / 60_000_000L);
        long additionalMinutes = Math.max(0L, totalSessionMinutes - lastQuery.accumulatedMinutes());
        if (additionalMinutes == 0L) {
            return Mono.empty();
        }
        String previousDate = dateFromTimestamp(lastQuery.timestampUs());
        String usageKey = usageKey(userId, lastQuery.domain(), previousDate);

        return redisTemplate.<String, String>opsForHash()
                .entries(usageKey)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .defaultIfEmpty(Map.of())
                .flatMap(current -> {
                    long totalDuration = parseLong(current.get("totalDuration"), 0L) + additionalMinutes;
                    long count = current.isEmpty()
                            ? Math.max(1L, lastQuery.queryCount())
                            : parseLong(current.get("count"), 0L);
                    Map<String, String> updates = Map.of(
                            "userId", userId,
                            "domain", lastQuery.domain(),
                            "firstAccess", String.valueOf(sessionStartUs),
                            "lastAccess", String.valueOf(currentTimestampUs),
                            "count", String.valueOf(count),
                            "totalDuration", String.valueOf(totalDuration),
                            "responseTimeSum", current.getOrDefault("responseTimeSum", "0"),
                            "avgResponseTimeMs", current.getOrDefault("avgResponseTimeMs", "0"));
                    return redisTemplate.<String, String>opsForHash()
                            .putAll(usageKey, updates)
                            .then(indexDomain(userId, lastQuery.domain(), previousDate))
                            .then();
                });
    }

    private Mono<Long> incrementPeriodStats(String userId, String domain, long timestampUs) {
        Instant instant = Instant.ofEpochMilli(timestampUs / 1000L);
        LocalDate date = instant.atZone(ANALYTICS_ZONE).toLocalDate();

        String day = date.toString();
        WeekFields weekFields = WeekFields.of(Locale.ROOT);
        int week = date.get(weekFields.weekOfWeekBasedYear());
        int weekYear = date.get(weekFields.weekBasedYear());
        String weekId = weekYear + "-W" + String.format("%02d", week);
        String monthId = date.format(MONTH_FORMAT);

        return Mono.when(
                        indexDomain(userId, domain, day),
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

    private Mono<Long> indexDomain(String userId, String domain, String date) {
        ReactiveSetOperations<String, String> setOps = redisTemplate.opsForSet();
        // 날짜별 인덱스와 전체 인덱스 모두 관리
        return setOps.add(userDomainsKey(userId, date), domain)
                .then(setOps.add(userDomainsKey(userId, null), domain));
    }

    public static String usageKey(String userId, String domain, String date) {
        return "usage:" + userId + ":" + domain + ":" + date;
    }

    /** date=null 이면 전체 인덱스 키 반환 */
    public static String userDomainsKey(String userId, String date) {
        if (date == null) return "usage:index:user:" + userId;
        return "usage:index:user:" + userId + ":" + date;
    }

    public static String statsKey(String period, String userId, String bucket) {
        return "stats:" + period + ":" + userId + ":" + bucket;
    }

    private Mono<LastQueryCache> readLastQuery(String userId, String domain) {
        return redisTemplate.<String, String>opsForHash()
                .entries(lastQueryKey(userId, domain))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .filter(values -> !values.isEmpty())
                .map(values -> new LastQueryCache(
                        values.getOrDefault("domain", ""),
                        parseLong(values.get("timestampUs"), 0L),
                        parseLong(values.get("sessionStartUs"), 0L),
                        parseLong(values.get("accumulatedMinutes"), 0L),
                        parseLong(values.get("queryCount"), 0L)))
                .defaultIfEmpty(new LastQueryCache("", 0L, 0L, 0L, 0L));
    }

    private Mono<Boolean> writeLastQuery(String userId, LastQueryCache lastQuery, String domain, long timestampUs) {
        long gapUs = timestampUs - lastQuery.timestampUs();
        boolean continueSession = !lastQuery.domain().isBlank()
                && gapUs > 0
                && gapUs <= sessionGapUs(domain);
        long sessionStartUs = continueSession && lastQuery.sessionStartUs() > 0
                ? lastQuery.sessionStartUs()
                : timestampUs;
        long accumulatedMinutes = continueSession
                ? Math.max(0L, (timestampUs - sessionStartUs) / 60_000_000L)
                : 0L;
        long queryCount = continueSession ? lastQuery.queryCount() + 1 : 1L;

        return writeLastQueryState(userId, domain, timestampUs, sessionStartUs, accumulatedMinutes, queryCount);
    }

    private Mono<Boolean> writeLastQueryState(
            String userId,
            String domain,
            long timestampUs,
            long sessionStartUs,
            long accumulatedMinutes,
            long queryCount) {
        String key = lastQueryKey(userId, domain);
        return redisTemplate.<String, String>opsForHash().putAll(
                        key,
                        Map.of(
                                "domain", domain,
                                "timestampUs", String.valueOf(timestampUs),
                                "sessionStartUs", String.valueOf(sessionStartUs),
                                "accumulatedMinutes", String.valueOf(accumulatedMinutes),
                                "queryCount", String.valueOf(queryCount)))
                .then(redisTemplate.expire(key, LAST_QUERY_TTL));
    }

    private String dateFromTimestamp(long timestampUs) {
        return Instant.ofEpochMilli(timestampUs / 1000L)
                .atZone(ANALYTICS_ZONE)
                .toLocalDate()
                .toString();
    }

    private String lastQueryKey(String userId, String domain) {
        return "usage:last-query:" + userId + ":" + domain;
    }

    private long sessionGapUs(String domain) {
        if ("youtube.com".equals(domain)) {
            return STREAMING_SESSION_GAP_US;
        }
        return SESSION_GAP_US;
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

    private record LastQueryCache(
            String domain,
            long timestampUs,
            long sessionStartUs,
            long accumulatedMinutes,
            long queryCount) {
    }
}
