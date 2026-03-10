# Detox-Agent AI 리뷰어 (Agent)

이 프로젝트는 사용자의 DNS 사용 데이터를 분석하여 디지털 디톡스를 돕는 AI 리뷰어 에이전트입니다. PydanticAI와 gRPC를 사용하여 실시간 스트리밍 분석 결과를 제공합니다.

## 주요 기능
- **DNS 사용 분석**: 사용자의 도메인 방문 기록(빈도, 시간)을 분석하여 위험 패턴 식별.
- **gRPC 스트리밍**: 분석 결과를 마크다운 형식으로 실시간 스트리밍 제공.
- **맞춤형 조언**: 디지털 중독 신호가 감지될 경우 단호하면서도 실질적인 대안 제시.

## 필수 요구 사항
- **Python 3.13+**
- **uv**: 빠른 패키지 관리를 위해 [uv](https://docs.astral.sh/uv/)가 설치되어 있어야 합니다.
- **OpenAI API Key**: `.env` 파일에 `OPENAI_API_KEY` 설정 필요. (`.env-example` 참고)

## 설치 및 실행 방법 (uv 기준)

### 1. 의존성 설치 및 환경 구성
```bash
# 의존성 설치 및 가상환경 생성
uv sync
```

### 2. gRPC 프로토콜 컴파일
gRPC 서버와 클라이언트 간 통신을 위한 코드를 생성해야 합니다.
```bash
uv run python -m grpc_tools.protoc -Isrc/proto --python_out=src/grpc --grpc_python_out=src/grpc ai_agent_review.proto usage.proto
```

### 3. 서버 실행
```bash
# gRPC(50052) 및 FastAPI(8000) 동시 실행
uv run main.py
```

## API 문서

### 1. gRPC 인터페이스 (Streaming)
- **Service**: `AiAgentReviewService`
- **Method**: `StreamReview`
- **Port**: `50052`
- **특징**: 마크다운 형식의 토큰을 실시간으로 스트리밍하며, 백엔드 UI 레이아웃을 위해 다음 섹션을 포함합니다:
  - `## 요약`, `## 위험 신호`, `## 실행 조언`

### 2. REST API (FastAPI)
- **Endpoint**: `GET /analyze/{period}`
- **Port**: `8000`
- **Period**: `daily`, `weekly`, `monthly`
- **응답 형식**: 구조화된 JSON (`UsageAnalysis` 모델)
  ```json
  {
    "is_addicted": true,
    "risk_level": "High",
    "addictive_domains": ["youtube.com"],
    "summary": "...",
    "recommendation": "...",
    "suggested_limit_seconds": 3600
  }
  ```

## 백엔드 통합 가이드 (gRPC)

백엔드 서버와 통신할 때 다음 사항이 중요합니다:

### 요청 데이터 (ReviewRequest)
- `UsageDto` 내의 `total_duration`은 **분(Minutes)** 단위로 전송해야 합니다.
- `top_domains` 리스트에 사용량이 많은 상위 도메인들을 포함시켜 주세요.

### 응답 포맷 (ReviewToken)
- AI 응답은 마크다운 형식의 토큰 스트림으로 전달됩니다.
- 응답 데이터는 백엔드 UI 레이아웃을 위해 다음 섹션을 반드시 포함합니다:
  - `## 요약`: 전체적인 사용 패턴 요약.
  - `## 위험 신호`: 중독이 의심되는 도메인 및 행동 식별.
  - `## 실행 조언`: 구체적인 디톡스 액션 플랜.

## 기술 스택
- **Framework**: FastAPI, PydanticAI
- **Communication**: gRPC (Streaming)
- **Model**: OpenAI GPT-4o
