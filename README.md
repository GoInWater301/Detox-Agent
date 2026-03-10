# 🧬 PrimerFlow

> **High-Performance PCR Primer Design & Visualization Platform**

PrimerFlow는 생명과학 연구원들이 PCR 프라이머를 더 빠르고 명확하게 설계할 수 있도록 돕는 웹 기반 플랫폼입니다.  
DNA 템플릿 시퀀스를 입력하면 프라이머 후보를 생성하고, 결과를 시각적으로 탐색할 수 있습니다.

## 프로젝트 개요

- DNA 템플릿 시퀀스 기반 PCR 프라이머 후보 생성
- 후보 위치를 결과 화면에서 시각적으로 확인
- 확대/축소 및 패닝으로 원하는 구간 탐색
- 프론트엔드와 백엔드가 분리된 구조로 설계 및 검증 로직 운영

## 주요 기능

### Step 1. Input Sequence
- 직접 입력, 파일 업로드, 붙여넣기 지원
- `A`, `T`, `G`, `C` 외 문자는 정리 대상
- 공백, 줄바꿈, FASTA 헤더(`>`) 자동 정리
- 유효한 염기서열이 없으면 다음 단계 및 생성 차단

### Step 2. Primer Properties
- GC, Tm, 농도 등 프라이머 조건 설정

### Step 3. Binding Location
- 결합 위치 관련 옵션 확인
- `Restriction Enzymes`는 Enter 또는 쉼표로 태그 입력
- 잘못 넣은 효소명은 태그 클릭으로 제거

### Step 4. Specificity & Preview
- 특이성 옵션 확인
- 캔버스 미리보기 및 확대/축소, 휠 줌, 드래그 패닝 지원

### Result
- `Generate Primers` 실행 후 결과 탭에서 후보 구간 시각화
- Zoom / Reset / Close 기반 결과 탐색 지원

## 프로젝트 구조

```text
frontend/
├─ app/
│  ├─ page.tsx
│  ├─ result/
│  │  ├─ page.tsx
│  │  └─ ResultClientPage.tsx
│  ├─ layout.tsx
│  └─ providers.tsx
├─ components/
│  ├─ canvas/GenomeCanvas.tsx
│  ├─ steps/
│  ├─ ui/
│  └─ PrimerResultModal.tsx
├─ src/
│  ├─ lib/
│  ├─ services/analysisService.ts
│  └─ types/
├─ store/useViewStore.ts
├─ hooks/
├─ tests/
backend/
├─ .github/
├─ .husky/
├─ app/
│  ├─ main.py
│  ├─ api/
│  │  └─ v1/
│  │     └─ endpoints/
│  │        ├─ design.py
│  │        └─ health.py
│  ├─ algorithms/
│  └─ schemas/
├─ database/
├─ scripts/
├─ tests/
├─ main.py
├─ requirements.txt
├─ README.md
└─ .gitignore
prompts/
spec/
strategy/
README.md
```

## 개발 환경 설정

### Frontend

#### 요구 사항
- Node.js 20.x 이상
- npm
- 로컬 백엔드 서버 `http://127.0.0.1:8000`

#### 설치 및 실행
```bash
npm ci
npm run dev
```

#### 스크립트
```bash
npm run dev
npm run build
npm run start
npm run lint
npm test
```

### Backend

#### 가상환경 생성

macOS / Linux / WSL 기준:

```bash
python3 -m venv .venv
source .venv/bin/activate
```

#### 의존성 설치 및 실행
```bash
pip install -r requirements.txt
uvicorn app.main:app --reload
```

- API: `http://localhost:8000`
- Swagger: `http://localhost:8000/docs`
- ReDoc: `http://localhost:8000/redoc`

## 기술 스택

### Frontend
- Next.js
- TypeScript
- React
- Tailwind CSS
- Zustand
- Axios
- TanStack Query
- Vitest

### Backend
- FastAPI
- Python
- Pydantic
- Uvicorn
- SQLite
- pysam

### Quality & Collaboration
- Ruff
- Pyright
- pytest
- commitlint
- Husky

## 주간 진행 상황

### Week 6 (26.1.26 - 2.1)
#### Frontend
- 대용량 시퀀스 렌더링 성능 최적화
- Binary Search 기반 뷰포트 탐색으로 고BP 구간 렌더링 부담 완화
- Canvas 배경 jittering 현상 수정

### Week 7 (26.2.2 - 2.8)
#### Frontend
- Step 1 입력 정규화 및 검증 UX 개선
- 붙여넣기/업로드 시 비정상 문자 처리 흐름 정리
- 대문자 ATGC 변환 및 sanitize 로직 고도화

### Week 8 (26.2.9 - 2.15)
#### Frontend
- Mock 응답 제거 후 실서버 응답 구조로 전환
- API 오류 메시지 및 상태 처리 보강
- UI 리뉴얼 및 테스트 기반 정비

#### Backend
- `app/` 기준으로 구조 정리
- CI 파이프라인 및 Ruff / Pyright 설정 추가
- `/health` 엔드포인트 테스트 추가

### Week 9 (26.2.16 - 2.22)
#### Frontend
- 의존성 보안 업데이트 및 취약점 대응
- Lint / Test / Build 기준 회귀 점검

#### Backend
- 원천 데이터 기반 `annotations.db` 구축 스크립트 추가
- DB 점검 및 통합 확인 스크립트 정리
- 데이터베이스 문서 보강

### Week 10 (26.2.23 - 3.1)
#### Frontend
- Step 3 제한효소 입력 이슈 수정
- 결과 표시를 모달에서 새 탭 방식으로 전환
- `amplicon` 용어를 `template`로 통일

#### Backend
- `health/db` 엔드포인트 추가
- 루트(`/`) 응답에 문서 및 헬스 체크 링크 제공

## Repobeats

### Frontend
(3.1 ~ 3.10 기간의 대시보드)

![Alt](https://repobeats.axiom.co/api/embed/d2f58146b1988f61e92f8b0545847f2f910bbb6d.svg "Repobeats analytics image")

### Backend
(3.1 ~ 3.10 기간의 대시보드)

![Alt](https://repobeats.axiom.co/api/embed/92bffc301187377261452dfd66e7fe73bbb2c8e3.svg "Repobeats analytics image")
