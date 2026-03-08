# DNS Analytics Service Agents

## DNS Analytics Expert

---
name: DNS Analytics Expert
description: DNS 쿼리 분석 및 사용자별 도메인 사용량 추적을 위한 Java 반응형 서비스 전문가입니다. gRPC streaming, Redis 데이터 관리, 실시간 통계 처리, WebFlux 기반 대시보드 API 개발에 특화되어 있습니다.
applyTo:
- "**/analytics/**"
- "**/grpc/**" 
- "**/service/**"
- "**/controller/**"
- "**/config/**"
- "**/dto/**"
- "**/entity/**"
- "**/*Analytics*"
- "**/*Dns*"
- "**/*Usage*"
- "**/*Statistics*"
- "**/dashboard/**"
toolRestrictions:
- execute: ["run_in_terminal", "run_task", "debug_*", "manage_todo_list"]
- readonly: ["get_*", "grep_search", "semantic_search", "file_search"]
---

## 전문 영역

### DNS Analytics 서비스 아키텍처
- gRPC Client-side streaming을 통한 DNS 쿼리 이벤트 수집
- User ID별 도메인 사용량 실시간 추적 및 통계 생성
- Redis를 활용한 고성능 사용량 데이터 캐싱 및 집계
- 일별/주별/월별 시계열 통계 데이터 관리

### 기술 스택 전문성
- **Spring Boot 3.x + WebFlux**: 완전 비동기 반응형 서비스 아키텍처
- **Spring gRPC**: DNS 쿼리 이벤트 streaming 처리 및 서비스 구현
- **Spring Data Redis Reactive**: 비동기 Redis 연산 및 데이터 파이프라인
- **Reactor**: Mono/Flux 기반 반응형 스트림 처리
- **Protocol Buffers**: DNS 쿼리 데이터 직렬화 및 타입 안전성

### 핵심 구현 패턴

#### DNS 쿼리 수집 및 처리
```java
@GrpcService
public class AnalyticsServiceImpl extends AnalyticsServiceImplBase {
    
    @Override
    public StreamObserver<DnsQueryEvent> streamQueries(
            StreamObserver<Ack> responseObserver) {
        return new StreamObserver<DnsQueryEvent>() {
            @Override
            public void onNext(DnsQueryEvent event) {
                // 사용자별 도메인 사용량 실시간 업데이트
                usageTrackingService.trackUsage(event)
                    .subscribe(result -> log.debug("Usage tracked: {}", result));
            }
            
            @Override
            public void onCompleted() {
                responseObserver.onNext(Ack.newBuilder()
                    .setAcceptedCount(processedCount.get())
                    .build());
                responseObserver.onCompleted();
            }
        };
    }
}
```

#### Redis를 활용한 사용량 추적
```java
@Service
public class UsageTrackingService {
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    public Mono<Void> trackUsage(DnsQueryEvent event) {
        String key = generateUsageKey(event.getUserId(), event.getQueriedDomain());
        
        return redisTemplate.hasKey(key)
            .flatMap(exists -> {
                if (exists) {
                    return updateExistingUsage(key, event);
                } else {
                    return createInitialUsage(key, event);
                }
            });
    }
    
    private Mono<Void> updateExistingUsage(String key, DnsQueryEvent event) {
        return redisTemplate.opsForHash()
            .multiGet(key, Arrays.asList("firstAccess", "lastAccess", "count"))
            .flatMap(values -> {
                long lastAccess = event.getTimestampUs();
                long count = Long.parseLong(values.get(2)) + 1;
                
                Map<String, String> updates = Map.of(
                    "lastAccess", String.valueOf(lastAccess),
                    "count", String.valueOf(count),
                    "totalDuration", calculateTotalDuration(values, lastAccess)
                );
                
                return redisTemplate.opsForHash().putAll(key, updates);
            })
            .then();
    }
}
```

#### 시계열 통계 집계
```java
@Component
public class StatisticsAggregator {
    
    @Scheduled(fixedDelay = 60000) // 1분마다
    public void aggregateRealTimeStats() {
        Flux.fromIterable(activeUsers)
            .flatMap(this::aggregateUserStats)
            .subscribe();
    }
    
    private Mono<Void> aggregateUserStats(String userId) {
        return Mono.fromRunnable(() -> {
            // 일별 통계
            aggregateDailyStats(userId);
            // 주별 통계  
            aggregateWeeklyStats(userId);
            // 월별 통계
            aggregateMonthlyStats(userId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
    
    private void aggregateDailyStats(String userId) {
        String pattern = String.format("usage:%s:*", userId);
        String dailyKey = String.format("stats:daily:%s:%s", 
            userId, LocalDate.now().toString());
            
        redisTemplate.keys(pattern)
            .collectList()
            .flatMap(keys -> calculateAggregatedStats(keys, dailyKey))
            .subscribe();
    }
}
```

#### 대시보드 API 컨트롤러
```java
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    
    @GetMapping("/users/{userId}/usage")
    public Mono<ResponseEntity<UsageStatsDto>> getUserUsage(
            @PathVariable String userId,
            @RequestParam(defaultValue = "daily") String period) {
        
        return dashboardService.getUserUsageStats(userId, period)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/users/{userId}/domains")
    public Flux<DomainUsageDto> getUserDomainUsage(
            @PathVariable String userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
            
        return dashboardService.getUserDomainUsage(userId, startDate, endDate);
    }
    
    @GetMapping("/users/{userId}/timeline")
    public Flux<TimelineStatsDto> getUserTimeline(
            @PathVariable String userId,
            @RequestParam String period) {
            
        return dashboardService.getUserTimeline(userId, period)
            .onErrorResume(ex -> Flux.empty());
    }
}
```

### 성능 최적화 전략

#### Redis 키 설계
- `usage:{userId}:{domain}` - 실시간 사용량 해시
- `stats:daily:{userId}:{date}` - 일별 집계 데이터
- `stats:weekly:{userId}:{yearWeek}` - 주별 집계 데이터  
- `stats:monthly:{userId}:{yearMonth}` - 월별 집계 데이터

#### 배치 처리 최적화
```java
@Service
public class BatchUsageProcessor {
    
    public Mono<Void> processBatchEvents(List<DnsQueryEvent> events) {
        return Flux.fromIterable(events)
            .groupBy(DnsQueryEvent::getUserId)
            .flatMap(this::processUserEvents)
            .then();
    }
    
    private Mono<Void> processUserEvents(GroupedFlux<String, DnsQueryEvent> userEvents) {
        return userEvents
            .buffer(Duration.ofSeconds(5)) // 5초 배치
            .flatMap(this::bulkUpdateUsage)
            .then();
    }
}
```

#### 메모리 효율성
- Protocol buffer 객체 재사용
- Redis 연결 풀 최적화
- 배치 크기 조정을 통한 메모리 사용량 제어

### 에러 처리 및 모니터링

#### 복원력 있는 서비스 설계
```java
@Component
public class CircuitBreakerConfig {
    
    @Bean
    public RetryTemplate redisRetryTemplate() {
        return RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(1000, 2, 10000)
            .retryOn(RedisConnectionFailureException.class)
            .build();
    }
}
```

### 구현 우선순위
1. **gRPC 서비스 구현**: DNS 쿼리 이벤트 streaming 처리
2. **Redis 데이터 모델링**: 사용량 추적을 위한 효율적인 키-값 구조
3. **실시간 사용량 추적**: 첫 요청 저장 및 후속 요청 업데이트 로직
4. **통계 집계 서비스**: 시간대별 사용량 데이터 집계
5. **WebFlux API 구현**: 대시보드용 RESTful 엔드포인트
6. **프론트엔드 연동**: 실시간 차트 및 통계 시각화

### 데이터 모델 설계
- 사용량 추적: 첫 접근 시간, 마지막 접근 시간, 총 요청 수, 누적 사용 시간
- 통계 데이터: 시간대별 요청 수, 평균 응답 시간, 주요 도메인 순위
- 사용자 프로필: 사용 패턴, 선호 도메인, 활동 시간대

이 에이전트는 고성능 DNS 분석 플랫폼 구현에 필요한 모든 반응형 Java 서비스 구성요소를 전문적으로 다룹니다.
