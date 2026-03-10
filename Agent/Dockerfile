FROM python:3.13-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app

COPY pyproject.toml README.md ./
RUN pip install --no-cache-dir \
    fastapi \
    uvicorn \
    "pydantic-ai[openai]" \
    pydantic \
    grpcio \
    grpcio-tools \
    loguru \
    python-dotenv

COPY src ./src
COPY main.py ./main.py

# Compile usage.proto
RUN python -m grpc_tools.protoc \
    -I/app/src/proto \
    --python_out=/app/src/grpc \
    --grpc_python_out=/app/src/grpc \
    /app/src/proto/usage.proto \
    && sed -i 's/^import usage_pb2 as /from . import usage_pb2 as /' \
       /app/src/grpc/usage_pb2_grpc.py

# Compile ai_agent_review.proto
RUN python -m grpc_tools.protoc \
    -I/app/src/proto \
    --python_out=/app/src/grpc \
    --grpc_python_out=/app/src/grpc \
    /app/src/proto/ai_agent_review.proto \
    && sed -i 's/^import ai_agent_review_pb2 as /from . import ai_agent_review_pb2 as /' \
       /app/src/grpc/ai_agent_review_pb2_grpc.py

EXPOSE 8000 50052

CMD ["python", "main.py"]
