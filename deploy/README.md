# Deployment Guide

## Docker Compose

```bash
cd /home/min/Workspace/Portfolio/Detox-Agent/Backend
cp .env.example .env
# 필요 시 .env 수정
docker compose up -d --build
```

Services:
- Frontend: `http://localhost:3000`
- WebServer API: `http://localhost:8080`
- Agent API: `http://localhost:8000`

Stop:

```bash
docker compose down
```

## Kubernetes

1) Build images:

```bash
docker build -t detox/webserver:latest ./webserver
docker build -t detox/agent:latest ./Agent
docker build -t detox/frontend:latest ./frontend
```

2) Apply manifests:

```bash
kubectl apply -k deploy/k8s
```

3) Access frontend:

- NodePort: `http://<NODE_IP>:30080`

Cleanup:

```bash
kubectl delete -k deploy/k8s
```
