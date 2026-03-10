package com.pnu.detox_agent.webserver.service;

import com.pnu.detox_agent.webserver.dto.DomainUsageDto;
import com.pnu.detox_agent.webserver.dto.TimelineStatsDto;
import com.pnu.detox_agent.webserver.dto.UsageStatsDto;
import com.pnu.detox_agent.webserver.repository.UserRepository;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class DashboardService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final ZoneId ANALYTICS_ZONE = ZoneId.of("Asia/Seoul");

    private final ReactiveStringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    public DashboardService(ReactiveStringRedisTemplate redisTemplate, UserRepository userRepository) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
    }

    public Mono<UsageStatsDto> getUserUsageStats(String userId, String period) {
        String normalizedPeriod = normalizePeriod(period);
        DateRange range = currentRange(normalizedPeriod);
        return resolveAnalyticsUserId(userId)
                .flatMap(resolvedUserId -> {
                    Flux<DomainUsageDto> domains = getUserDomainUsageInternal(
                            resolvedUserId,
                            range.start().toString(),
                            range.end().toString());
                    Mono<Long> totalQueries = currentTotalQueries(resolvedUserId, normalizedPeriod);

                    return domains.collectList().zipWith(totalQueries)
                            .map(tuple -> {
                                List<DomainUsageDto> sorted = tuple.getT1().stream()
                                        .sorted(Comparator.comparingLong(DomainUsageDto::requestCount).reversed())
                                        .toList();
                                long fallbackTotal = sorted.stream().mapToLong(DomainUsageDto::requestCount).sum();
                                long total = tuple.getT2() > 0 ? tuple.getT2() : fallbackTotal;
                                return new UsageStatsDto(
                                        userId,
                                        normalizedPeriod,
                                        total,
                                        sorted.size(),
                                        sorted.stream().limit(10).toList());
                            });
                });
    }

    public Flux<DomainUsageDto> getUserDomainUsage(String userId, String startDate, String endDate) {
        return resolveAnalyticsUserId(userId)
                .flatMapMany(resolvedUserId -> getUserDomainUsageInternal(resolvedUserId, startDate, endDate));
    }

    private Flux<DomainUsageDto> getUserDomainUsageInternal(String analyticsUserId, String startDate, String endDate) {
        LocalDate start = parseDate(startDate, LocalDate.now(ANALYTICS_ZONE));
        LocalDate end = parseDate(endDate, LocalDate.now(ANALYTICS_ZONE));

        List<LocalDate> dates = start.datesUntil(end.plusDays(1)).toList();
        return Flux.fromIterable(dates)
                .flatMap(date -> {
                    String dateStr = date.toString();
                    String indexKey = UsageTrackingService.userDomainsKey(analyticsUserId, dateStr);
                    return redisTemplate.opsForSet().members(indexKey)
                            .flatMap(domain -> loadDomainUsageForDate(analyticsUserId, domain, dateStr));
                })
                .groupBy(DomainUsageDto::domain)
                .flatMap(group -> group.reduce(this::mergeDomainUsage));
    }

    public Flux<TimelineStatsDto> getUserTimeline(String userId, String period) {
        String normalizedPeriod = normalizePeriod(period);
        return resolveAnalyticsUserId(userId)
                .flatMapMany(resolvedUserId -> {
                    String pattern = UsageTrackingService.statsKey(normalizedPeriod, resolvedUserId, "*");

                    return redisTemplate.keys(pattern)
                            .flatMap(key -> redisTemplate.<String, String>opsForHash()
                                    .entries(key)
                                    .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                                    .map(map -> {
                                        long total = parseLong(map.get("totalQueries"), 0L);
                                        long unique = map.keySet().stream().filter(k -> k.startsWith("domain:")).count();
                                        String bucket = key.substring(key.lastIndexOf(':') + 1);
                                        return new TimelineStatsDto(bucket, total, unique);
                                    }))
                            .sort(Comparator.comparing(TimelineStatsDto::bucket));
                });
    }

    private Mono<Long> currentTotalQueries(String userId, String period) {
        String bucket = currentBucket(period);
        String key = UsageTrackingService.statsKey(period, userId, bucket);
        return redisTemplate.<String, String>opsForHash().get(key, "totalQueries").map(v -> parseLong(v, 0L)).defaultIfEmpty(0L);
    }

    private String currentBucket(String period) {
        LocalDate now = LocalDate.now(ANALYTICS_ZONE);
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

    private DateRange currentRange(String period) {
        LocalDate now = LocalDate.now(ANALYTICS_ZONE);
        return switch (period) {
            case "weekly" -> {
                WeekFields weekFields = WeekFields.of(Locale.ROOT);
                LocalDate start = now.with(weekFields.dayOfWeek(), 1);
                yield new DateRange(start, start.plusDays(6));
            }
            case "monthly" -> new DateRange(
                    now.with(TemporalAdjusters.firstDayOfMonth()),
                    now.with(TemporalAdjusters.lastDayOfMonth()));
            default -> new DateRange(now, now);
        };
    }

    private DomainUsageDto toDomainUsageDto(String domain, Map<String, String> values) {
        return new DomainUsageDto(
                domain,
                parseLong(values.get("count"), 0L),
                parseLong(values.get("firstAccess"), 0L),
                parseLong(values.get("lastAccess"), 0L),
                parseLong(values.get("totalDuration"), 0L),
                parseLong(values.get("avgResponseTimeMs"), 0L));
    }

    private DomainUsageDto mergeDomainUsage(DomainUsageDto a, DomainUsageDto b) {
        long count = a.requestCount() + b.requestCount();
        long firstAccess = (a.firstAccess() > 0 && b.firstAccess() > 0)
                ? Math.min(a.firstAccess(), b.firstAccess())
                : Math.max(a.firstAccess(), b.firstAccess());
        long lastAccess = Math.max(a.lastAccess(), b.lastAccess());
        long totalDuration = a.totalDuration() + b.totalDuration();
        long avgResponse = count == 0 ? 0 : (a.averageResponseTimeMs() * a.requestCount()
                + b.averageResponseTimeMs() * b.requestCount()) / count;
        return new DomainUsageDto(a.domain(), count, firstAccess, lastAccess, totalDuration, avgResponse);
    }

    private Mono<DomainUsageDto> loadDomainUsageForDate(String analyticsUserId, String domain, String dateStr) {
        String usageKey = UsageTrackingService.usageKey(analyticsUserId, domain, dateStr);
        return redisTemplate.<String, String>opsForHash()
                .entries(usageKey)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(values -> {
                    if (!values.isEmpty()) {
                        return Mono.just(toDomainUsageDto(domain, values));
                    }
                    return redisTemplate.<String, String>opsForHash()
                            .get(UsageTrackingService.statsKey("daily", analyticsUserId, dateStr), "domain:" + domain)
                            .map(count -> new DomainUsageDto(domain, parseLong(count, 0L), 0L, 0L, 0L, 0L))
                            .defaultIfEmpty(new DomainUsageDto(domain, 0L, 0L, 0L, 0L, 0L));
                })
                .filter(dto -> dto.requestCount() > 0);
    }

    private LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String normalizePeriod(String period) {
        if (period == null) {
            return "daily";
        }
        return switch (period.toLowerCase(Locale.ROOT)) {
            case "weekly" -> "weekly";
            case "monthly" -> "monthly";
            default -> "daily";
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

    private Mono<String> resolveAnalyticsUserId(String userId) {
        return userRepository.findByUsername(userId)
                .mapNotNull(user -> user.getDohToken())
                .filter(token -> !token.isBlank())
                .switchIfEmpty(Mono.just(userId));
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}
