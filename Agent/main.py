from dotenv import load_dotenv
load_dotenv()
import asyncio
import logging

import uvicorn

from src.api.main_api import app as fastapi_app
from src.grpc.ai_review_server import serve as serve_grpc

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)


async def main():
    grpc_task = asyncio.create_task(serve_grpc(port=50052))

    config = uvicorn.Config(
        fastapi_app,
        host="0.0.0.0",
        port=8000,
        log_level="info",
    )
    server = uvicorn.Server(config)

    await asyncio.gather(grpc_task, server.serve())


if __name__ == "__main__":
    asyncio.run(main())
