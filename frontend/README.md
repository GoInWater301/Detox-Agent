# Detox-Agent Frontend (React)

Detox-Agent 백엔드(`webserver`)와 연동되는 React + Vite 프론트엔드 개발 서버입니다.

## 실행

```bash
cd frontend
npm install
npm run dev
```

- 기본 URL: `http://localhost:5173`
- `/api` 요청은 기본적으로 `http://localhost:8080`으로 프록시됩니다.

## 환경 변수

- `VITE_BACKEND_URL`: API 프록시 타깃 변경 (기본값: `http://localhost:8080`)

예시:

```bash
VITE_BACKEND_URL=http://localhost:18080 npm run dev
```

## 빌드

```bash
npm run build
npm run preview
```
