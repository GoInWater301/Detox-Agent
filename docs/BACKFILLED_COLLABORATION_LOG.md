# Backfilled Collaboration Log

이 문서는 2026년 3월 11일 기준으로 정리한 사후 협업 기록 초안이다.

- 목적: Week 7부터 Week 10까지의 작업 흐름을 GitHub `issue`와 `pull request` 단위로 일관되게 정리
- 원칙: 실제 개발 결과와 현재 저장소 상태를 기준으로 작성하고, `Backfilled` 라벨을 사용해 사후 정리 기록임을 명시
- 저장소 소유자 연락: `lmh2157@outlook.kr`

## Label Set

- `backfilled`: 사후 정리된 이슈/PR
- `week-7`
- `week-8`
- `week-9`
- `week-10`
- `backend`
- `agent`
- `doh`
- `documentation`
- `integration`

## Milestones

- `Week 7 - Service Architecture Alignment`
- `Week 8 - Auth and API Skeleton`
- `Week 9 - Usage Aggregation and Storage`
- `Week 10 - Dashboard and AI Review Integration`

## Week 7

### Issue 1

- Title: `[Backfilled][Week 7] 서비스 경계와 데이터 플로우 정의`
- Labels: `backfilled`, `week-7`, `documentation`, `integration`
- Assignee: `leeSwallow`
- Summary:
  - DoH, WebServer, Agent 간의 책임과 데이터 이동 경로를 정의
  - gRPC 기반 통신 지점과 대시보드 API 범위를 정리
  - 초기 로컬 실행 구조를 합의

### Issue 2

- Title: `[Backfilled][Week 7] 백엔드 저장소 구조와 실행 단위 확정`
- Labels: `backfilled`, `week-7`, `backend`, `integration`
- Assignee: `leeSwallow`
- Summary:
  - `DoH`, `webserver`, `Agent`, `deploy`, `scripts` 디렉터리 역할 정의
  - 로컬 개발 기준 엔트리포인트와 빌드 단위 분리
  - 문서 구조와 서비스별 README 초안 방향 결정

### PR 1

- Branch: `backfill/week7-architecture-alignment`
- Title: `[Backfilled][Week 7] 서비스 아키텍처와 저장소 구조 정리`
- Links:
  - Closes Issue 1
  - Closes Issue 2
- Description:
  - 전체 서비스 경계와 데이터 흐름을 문서화
  - 저장소 구조와 서비스 책임을 루트 문서 기준으로 정렬
  - 후속 구현이 가능한 최소 통합 기준을 확정

#### Issue Body Draft

```md
## Summary
DetoxAgent의 초기 개발 범위를 기준으로 DoH, WebServer, Agent 간 서비스 경계와 데이터 플로우를 정리한다. 이후 주차 구현에서 동일한 기준을 재사용할 수 있도록 저장소 구조와 로컬 실행 단위를 함께 맞춘다.

## Scope
- DoH, WebServer, Agent의 책임 구분
- gRPC 및 REST 통신 지점 정의
- 저장소 최상위 구조와 서비스별 문서 기준 확정

## Expected Outcome
- 후속 구현이 문서 기준으로 연결될 수 있는 최소 아키텍처 확보
- 대시보드, 인증, AI 리뷰 흐름을 하나의 사용자 여정으로 설명 가능
```

#### PR Body Draft

```md
# Backfilled From

- Week: `Week 7`
- Owner Contact: `lmh2157@outlook.kr`

# Summary

- 서비스 경계, 데이터 흐름, 저장소 구조를 기준 문서로 정리했다.

# Changes

- DoH, WebServer, Agent 간 책임과 연동 방향을 문서 기준으로 정렬
- 루트 README와 개요 문서에서 사용자 흐름과 서비스 구성을 일관되게 정리
- 후속 주차 구현에 사용할 최소 로컬 실행 구조를 확정

# Verification

- [ ] README와 개요 문서의 서비스 설명이 서로 충돌하지 않는지 확인
- [ ] 저장소 최상위 구조가 실제 디렉터리 구성과 일치하는지 확인

# Linked Issues

- Closes Issue 1
- Closes Issue 2

# Notes

- `backfilled`, `week-7` 라벨 적용
- milestone: `Week 7 - Service Architecture Alignment`
```

#### Merge Commit Draft

```text
merge: align service architecture and repository structure for week 7
```

## Week 8

### Issue 3

- Title: `[Backfilled][Week 8] 사용자 인증과 DoH 엔드포인트 발급 흐름 정의`
- Labels: `backfilled`, `week-8`, `backend`, `integration`
- Assignee: `leeSwallow`
- Summary:
  - 회원가입, 로그인, JWT 발급 흐름 정의
  - 사용자별 DoH 토큰 기반 URL 발급 정책 정리
  - WebServer API 입출력 초안 설계

### Issue 4

- Title: `[Backfilled][Week 8] WebServer 중심 REST/gRPC 인터페이스 초안 구성`
- Labels: `backfilled`, `week-8`, `backend`, `integration`
- Assignee: `leeSwallow`
- Summary:
  - 인증 API와 대시보드 API 기본 경로 확정
  - DoH와 Agent 연동을 위한 gRPC 인터페이스 초안 구성
  - 프론트 화면에서 소비할 응답 구조 정렬

### PR 2

- Branch: `backfill/week8-auth-api-skeleton`
- Title: `[Backfilled][Week 8] 인증 흐름과 API 스켈레톤 정리`
- Links:
  - Closes Issue 3
  - Closes Issue 4
- Description:
  - 사용자 인증과 DoH URL 발급 흐름을 문서와 코드 구조에 반영
  - WebServer 기준의 REST/gRPC 인터페이스 초안을 정리
  - 대시보드 연동을 위한 응답 형식의 기준점을 마련

#### Issue Body Draft

```md
## Summary
사용자 인증과 개인별 DoH 엔드포인트 발급 흐름을 정리하고, WebServer를 중심으로 REST/gRPC 인터페이스의 초안을 맞춘다. 화면 연동 전에 필요한 API 응답 구조를 먼저 고정하는 것이 목표다.

## Scope
- 회원가입, 로그인, JWT 발급 흐름 정의
- 사용자별 DoH URL 발급 정책 정리
- WebServer 기준 API path와 응답 구조 초안 작성

## Expected Outcome
- 인증 후 대시보드 및 DNS 수집 흐름으로 자연스럽게 연결되는 사용자 시나리오 확보
- 이후 템플릿 및 AI 리뷰 연동에서 재사용 가능한 인터페이스 기준 정립
```

#### PR Body Draft

```md
# Backfilled From

- Week: `Week 8`
- Owner Contact: `lmh2157@outlook.kr`

# Summary

- 인증 흐름과 API 스켈레톤을 정리해 서비스 간 연동 기준을 맞췄다.

# Changes

- 회원가입, 로그인, JWT 기반 인증 흐름을 서비스 문서와 구현 구조 기준으로 정리
- 사용자별 DoH 엔드포인트 발급 정책과 WebServer API 흐름을 연결
- 대시보드에서 사용할 기본 응답 구조와 gRPC 연동 포인트를 정의

# Verification

- [ ] 인증 관련 엔드포인트 문서가 현재 구현 범위와 일치하는지 확인
- [ ] WebServer 중심의 REST/gRPC 설명이 README 및 개요 문서와 일치하는지 확인

# Linked Issues

- Closes Issue 3
- Closes Issue 4

# Notes

- `backfilled`, `week-8` 라벨 적용
- milestone: `Week 8 - Auth and API Skeleton`
```

#### Merge Commit Draft

```text
merge: define auth flow and api skeleton for week 8
```

## Week 9

### Issue 5

- Title: `[Backfilled][Week 9] DNS 이벤트 적재와 사용량 집계 경로 보강`
- Labels: `backfilled`, `week-9`, `backend`, `doh`, `integration`
- Assignee: `leeSwallow`
- Summary:
  - DNS 이벤트 수집 이후 Redis/PostgreSQL 적재 흐름 정리
  - 사용자별 기간 통계와 도메인별 집계 요구사항 구체화
  - 집계 서비스와 저장 계층 책임 분리

### Issue 6

- Title: `[Backfilled][Week 9] 차단 목록 연동을 고려한 도메인 데이터 구조 정리`
- Labels: `backfilled`, `week-9`, `backend`, `doh`
- Assignee: `leeSwallow`
- Summary:
  - 차단 도메인 관리와 사용량 분석이 함께 동작할 수 있는 구조 검토
  - Redis 기반 필터링과 WebServer API 연결 지점 정의
  - 후속 blocklist CRUD를 위한 스키마 방향 정리

### PR 3

- Branch: `backfill/week9-usage-aggregation`
- Title: `[Backfilled][Week 9] 사용량 집계 및 도메인 처리 흐름 보강`
- Links:
  - Closes Issue 5
  - Closes Issue 6
- Description:
  - DNS 이벤트 적재부터 집계 조회까지의 흐름을 보강
  - Redis/PostgreSQL 역할을 구분해 대시보드 조회 기반을 정리
  - blocklist 연동을 고려한 도메인 처리 정책을 맞춤

#### Issue Body Draft

```md
## Summary
DNS 이벤트가 수집된 뒤 Redis와 PostgreSQL로 어떻게 적재되고 집계되는지 경로를 보강한다. 사용량 조회, 기간 통계, 도메인 분석과 차단 목록 관리가 같은 데이터 흐름 위에서 설명되도록 정리한다.

## Scope
- DNS 이벤트 적재 경로 정리
- Redis/PostgreSQL 역할 분리
- 도메인 통계와 차단 목록 연동을 고려한 데이터 구조 보완

## Expected Outcome
- 대시보드 사용량 조회와 blocklist 관리가 동일한 백엔드 모델 위에서 설명 가능
- WebServer와 DoH 간 책임이 집계 기준으로 명확해짐
```

#### PR Body Draft

```md
# Backfilled From

- Week: `Week 9`
- Owner Contact: `lmh2157@outlook.kr`

# Summary

- DNS 이벤트 적재, 사용량 집계, 도메인 처리 정책을 보강했다.

# Changes

- Redis/PostgreSQL을 함께 사용하는 사용량 집계 경로를 정리
- 사용자별 기간 통계와 도메인 분석 흐름을 대시보드 조회 기준으로 맞춤
- blocklist 연동을 고려한 도메인 데이터 처리 구조를 정리

# Verification

- [ ] 사용량 조회 관련 문서가 저장 계층 설명과 일치하는지 확인
- [ ] blocklist 관련 설명이 DoH 필터링 역할과 충돌하지 않는지 확인

# Linked Issues

- Closes Issue 5
- Closes Issue 6

# Notes

- `backfilled`, `week-9` 라벨 적용
- milestone: `Week 9 - Usage Aggregation and Storage`
```

#### Merge Commit Draft

```text
merge: reinforce usage aggregation and domain processing for week 9
```

## Week 10

### Issue 7

- Title: `[Backfilled][Week 10] 대시보드 UI와 인증 화면을 API 흐름 기준으로 연결`
- Labels: `backfilled`, `week-10`, `backend`, `integration`
- Assignee: `leeSwallow`
- Summary:
  - 로그인, 대시보드, 리뷰, 차단 목록 화면의 라우팅 정리
  - API 응답 구조와 템플릿 렌더링 연결
  - 로컬 통합 실행 기준을 화면 검증 흐름과 맞춤

### Issue 8

- Title: `[Backfilled][Week 10] AI 리뷰 스트리밍과 서비스 로컬 통합 실행 구성`
- Labels: `backfilled`, `week-10`, `agent`, `integration`
- Assignee: `leeSwallow`
- Summary:
  - AI 리뷰 요청을 WebServer와 Agent 사이의 스트리밍 흐름으로 연결
  - Docker Compose와 로컬 스크립트 기반 실행 흐름 정리
  - 다중 서비스 포트와 실행 순서를 개발 환경에 맞게 고정

### PR 4

- Branch: `backfill/week10-dashboard-ai-integration`
- Title: `[Backfilled][Week 10] 대시보드 연동과 AI 리뷰 통합 흐름 정리`
- Links:
  - Closes Issue 7
  - Closes Issue 8
- Description:
  - 템플릿 기반 UI를 실제 API 흐름에 맞춰 연결
  - AI 리뷰 스트리밍 구조와 로컬 실행 절차를 정리
  - 사용자 관점에서 인증, 사용량 조회, 리뷰 흐름이 이어지도록 정돈

#### Issue Body Draft

```md
## Summary
Week 8~9에서 정리한 인증, 집계, 도메인 처리 흐름을 실제 사용자 화면과 AI 리뷰 요청 흐름으로 연결한다. 대시보드, 리뷰, 차단 목록 UI가 로컬 환경에서 함께 검증될 수 있도록 서비스 실행 기준도 정리한다.

## Scope
- 대시보드/리뷰/차단 목록 화면과 API 연결
- Agent 기반 AI 리뷰 스트리밍 흐름 정리
- Docker Compose 및 로컬 실행 스크립트 기준 정리

## Expected Outcome
- 로그인부터 사용량 조회, AI 리뷰까지 이어지는 사용자 흐름 정리
- 로컬 통합 실행 기준이 문서와 실제 구성 모두에서 일관되게 유지
```

#### PR Body Draft

```md
# Backfilled From

- Week: `Week 10`
- Owner Contact: `lmh2157@outlook.kr`

# Summary

- 대시보드와 AI 리뷰 흐름을 실제 사용자 시나리오 기준으로 연결했다.

# Changes

- 템플릿 기반 화면을 API 응답 구조와 맞춰 연결
- Agent와 WebServer 사이의 AI 리뷰 스트리밍 흐름을 정리
- Docker Compose 및 로컬 실행 스크립트 기준으로 다중 서비스 실행 절차를 문서화

# Verification

- [ ] 대시보드, 리뷰, 차단 목록 관련 문서가 현재 서비스 구성과 일치하는지 확인
- [ ] 로컬 실행 절차가 README와 scripts 문서 사이에서 충돌하지 않는지 확인

# Linked Issues

- Closes Issue 7
- Closes Issue 8

# Notes

- `backfilled`, `week-10` 라벨 적용
- milestone: `Week 10 - Dashboard and AI Review Integration`
```

#### Merge Commit Draft

```text
merge: connect dashboard and ai review flow for week 10
```

## Merge Sequence

다음 순서로 `main`에 병합된 기록처럼 정리한다.

1. `backfill/week7-architecture-alignment`
2. `backfill/week8-auth-api-skeleton`
3. `backfill/week9-usage-aggregation`
4. `backfill/week10-dashboard-ai-integration`

## PR Body Checklist

각 PR 본문에는 아래 항목을 포함한다.

- `Backfilled from Week X`
- 작업 배경
- 핵심 변경점 3개 이내
- 검증 항목
- 후속 작업
- `Owner Contact: lmh2157@outlook.kr`
