package com.pnu.detox_agent.webserver.service;

import com.pnu.detox_agent.webserver.dto.DomainUsageDto;
import com.pnu.detox_agent.webserver.dto.TimelineStatsDto;
import com.pnu.detox_agent.webserver.dto.UsageStatsDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    private final ReactiveStringRedisTemplate redisTemplate;

    public DashboardService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<UsageStatsDto> getUserUsageStats(String userId, String period) {
        Flux<DomainUsageDto> domains = getUserDomainUsage(userId, null, null);

        Mono<Long> totalQueries = currentTotalQueries(userId, normalizePeriod(period));

        return domains.collectList().zipWith(totalQueries)
                .map(tuple -> {
                    List<DomainUsageDto> sorted = tuple.getT1().stream()
                            .sorted(Comparator.comparingLong(DomainUsageDto::requestCount).reversed())
                            .toList();
                    long fallbackTotal = sorted.stream().mapToLong(DomainUsageDto::requestCount).sum();
                    long total = tuple.getT2() > 0 ? tuple.getT2() : fallbackTotal;
                    return new UsageStatsDto(
                            userId,
                            normalizePeriod(period),
                            total,
                            sorted.size(),
                            sorted.stream().limit(10).toList());
                });
    }

    public Flux<DomainUsageDto> getUserDomainUsage(String userId, String startDate, String endDate) {
        LocalDate start = parseDate(startDate, LocalDate.MIN);
        LocalDate end = parseDate(endDate, LocalDate.MAX);

        return redisTemplate.opsForSet().members(UsageTrackingService.userDomainsKey(userId))
                .flatMap(domain -> redisTemplate.<String, String>opsForHash()
                        .entries(UsageTrackingService.usageKey(userId, domain))
                        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                        .filter(values -> !values.isEmpty())
                        .map(values -> toDomainUsageDto(domain, values))
                        .filter(dto -> inRange(dto, start, end)));
    }

    public Flux<TimelineStatsDto> getUserTimeline(String userId, String period) {
        String normalizedPeriod = normalizePeriod(period);
        String pattern = UsageTrackingService.statsKey(normalizedPeriod, userId, "*");

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
    }

    private Mono<Long> currentTotalQueries(String userId, String period) {
        String bucket = currentBucket(period);
        String key = UsageTrackingService.statsKey(period, userId, bucket);
        return redisTemplate.<String, String>opsForHash().get(key, "totalQueries").map(v -> parseLong(v, 0L)).defaultIfEmpty(0L);
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

    private DomainUsageDto toDomainUsageDto(String domain, Map<String, String> values) {
        return new DomainUsageDto(
                domain,
                parseLong(values.get("count"), 0L),
                parseLong(values.get("firstAccess"), 0L),
                parseLong(values.get("lastAccess"), 0L),
                parseLong(values.get("totalDuration"), 0L),
                parseLong(values.get("avgResponseTimeMs"), 0L));
    }

    private boolean inRange(DomainUsageDto dto, LocalDate start, LocalDate end) {
        if (dto.lastAccess() <= 0) {
            return true;
        }
        LocalDate accessDate = Instant.ofEpochMilli(dto.lastAccess() / 1000L).atZone(ZoneOffset.UTC).toLocalDate();
        return !accessDate.isBefore(start) && !accessDate.isAfter(end);
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
}
