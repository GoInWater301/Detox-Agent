import asyncio
import logging
import os
import grpc
from dotenv import load_dotenv
from grpc import aio

# Load .env at module level
load_dotenv()

logger = logging.getLogger(__name__)

# Lazy-import generated proto modules (compiled by Dockerfile)
try:
    from src.grpc import ai_agent_review_pb2 as pb2
    from src.grpc import ai_agent_review_pb2_grpc as pb2_grpc
except ImportError:
    pb2 = None
    pb2_grpc = None


def _build_prompt(request) -> str:
    usage = request.usage
    total_minutes = sum(max(d.total_duration, 0) for d in list(usage.top_domains))
    domain_lines = []
    for d in list(usage.top_domains)[:10]:
        minutes = max(d.total_duration, 0)
        domain_lines.append(
            f"  - {d.domain}: {d.request_count:,}회 요청, 약 {minutes:.0f}분 사용, 평균 응답 시간 {d.average_response_time_ms}ms"
        )
    domains_text = "\n".join(domain_lines) or "  (도메인 데이터가 없습니다)"
    period = usage.period or "선택된 기간"
    user_prompt = request.prompt or (
        f"사용자의 {period} DNS 사용 패턴을 분석하고, 위험한 도메인이나 습관적인 행동을 식별하여 "
        "구체적인 디지털 디톡스 방안을 제안하세요."
    )

    return (
        "당신은 DNS 기반의 디지털 사용 데이터를 리뷰하는 전문가입니다.\n"
        "total_duration은 마이크로초나 밀리초가 아닌 '분(MINUTES)' 단위로 간주하세요.\n"
        "응답은 실용적이고 간결하게 작성하세요. 반드시 다음 섹션을 포함한 마크다운 형식을 사용하세요:\n"
        "## 요약\n"
        "## 위험 신호\n"
        "## 실행 조언\n\n"
        f"기간: {period}\n"
        f"사용자 ID: {usage.user_id or 'unknown'}\n"
        f"총 DNS 쿼리: {usage.total_queries:,}\n"
        f"고유 도메인 방문: {usage.unique_domains}\n"
        f"추정 총 사용 시간: {total_minutes:.0f}분\n\n"
        f"주요 도메인:\n{domains_text}\n\n"
        f"사용자 요청: {user_prompt}"
    )


def _mock_stream_texts(request) -> list[str]:
    usage = request.usage
    top = list(usage.top_domains)[:3]
    top_names = ", ".join(d.domain for d in top) if top else "다양한 도메인"
    total_minutes = sum(max(d.total_duration, 0) for d in top)
    return [
        "⚠️ **AI 분석을 사용할 수 없습니다** — OpenAI API 키가 설정되지 않았습니다.\n\n",
        "## 요약\n\n",
        f"- 기간: **{usage.period or '선택된 기간'}**\n",
        f"- 총 DNS 쿼리: **{usage.total_queries:,}회**\n",
        f"- 고유 도메인 수: **{usage.unique_domains}개**\n",
        f"- 추정 사용 시간: **{total_minutes:.0f}분**\n\n",
        "## 위험 신호\n\n",
        f"- 가장 활발한 도메인: {top_names}\n",
        "- 반복적인 엔터테인먼트 또는 미디어 도메인 방문은 집중력 분산을 의미할 수 있습니다.\n\n",
        "## 실행 조언\n\n",
        "1. 자주 방문하는 엔터테인먼트 사이트에 대해 사용 시간 제한을 설정하세요.\n",
        "2. 소셜 미디어 접속 없이 집중할 수 있는 업무 시간을 계획하세요.\n",
        "3. 어떤 도메인이 실제로 생산성에 필요한지 검토해 보세요.\n\n",
        "_개인화된 AI 분석을 활성화하려면 `OPENAI_API_KEY`를 설정하세요._",
    ]


from src.ai.agent import run_analysis, usage_analyst_agent
from src.models import DomainUsageItem

class AiAgentReviewServicer(pb2_grpc.AiAgentReviewServiceServicer):
    def __init__(self):
        self.agent = None
        # Always check env at init time
        api_key = os.getenv("OPENAI_API_KEY")
        
        if api_key and not api_key.startswith("your_"):
            try:
                from pydantic_ai import Agent as PydanticAgent

                self.agent = PydanticAgent(
                    "openai:gpt-4o",
                    system_prompt=(
                        "당신은 디지털 헬스 및 생산성 코치 전문가입니다. "
                        "사용자의 DNS 사용 데이터를 분석하고 명확하고 실행 가능한 디톡스 조언을 제공하세요. "
                        "문제 패턴(소셜 미디어, 게임, 스트리밍)을 식별하고 구체적이고 현실적인 대안을 제안하세요. "
                        "공감하면서도 단호한 어조를 유지하세요. "
                        "백엔드에서 보내는 total_duration은 '분' 단위입니다. "
                        "헤더와 불렛 포인트를 포함한 마크다운 형식을 사용하세요."
                    ),
                )
                logger.info("PydanticAI agent initialized for streaming reviews")
            except Exception as e:
                logger.warning("Failed to initialize PydanticAI agent: %s", e)

    async def AnalyzeUsage(self, request, context):
        """Structured analysis of domain usage data."""
        usage_dto = request.usage
        domain_items = [
            DomainUsageItem(
                domain=d.domain,
                duration_seconds=d.total_duration * 60,  # Convert minutes to seconds for internal model
                last_accessed="" # UsageDto doesn't have this, but our model needs it
            )
            for d in usage_dto.top_domains
        ]

        try:
            # Use the structured agent from src/ai/agent.py
            analysis = await run_analysis(domain_items)
            
            blocking_recs = [
                pb2.BlockingRecommendation(
                    domain=rec.domain,
                    reason=rec.reason,
                    recommended_limit_minutes=rec.recommended_limit_minutes or 0
                )
                for rec in analysis.blocking_recommendations
            ]

            return pb2.AnalysisResponse(
                is_addicted=analysis.is_addicted,
                risk_level=analysis.risk_level,
                summary=analysis.summary,
                recommendation=analysis.recommendation,
                addictive_domains=analysis.addictive_domains,
                blocking_recommendations=blocking_recs
            )
        except Exception as e:
            logger.error("AnalyzeUsage error: %s", e)
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Analysis failed: {str(e)}")
            return pb2.AnalysisResponse()

    async def StreamReview(self, request, context):
        model_name = "gpt-4o" if self.agent else "mock"
        session_id = request.session_id or "review"

        if self.agent is None:
            for text in _mock_stream_texts(request):
                yield pb2.ReviewToken(
                    token=text, done=False, model=model_name, message_id=session_id
                )
                await asyncio.sleep(0.05)
            yield pb2.ReviewToken(
                token="", done=True, model=model_name, message_id=session_id
            )
            return

        prompt = _build_prompt(request)
        try:
            async with self.agent.run_stream(prompt) as result:
                async for delta in result.stream_text(delta=True):
                    yield pb2.ReviewToken(
                        token=delta, done=False, model=model_name, message_id=session_id
                    )
        except Exception as e:
            logger.error("StreamReview error: %s", e)
            yield pb2.ReviewToken(
                token=f"\n\n[Error during AI analysis: {e}]",
                done=False,
                model=model_name,
                message_id=session_id,
            )

        yield pb2.ReviewToken(
            token="", done=True, model=model_name, message_id=session_id
        )


async def serve(port: int = 50051):
    if pb2 is None or pb2_grpc is None:
        logger.error("Proto modules not found — gRPC server not started")
        return

    server = aio.server()
    pb2_grpc.add_AiAgentReviewServiceServicer_to_server(
        AiAgentReviewServicer(), server
    )
    listen_addr = f"[::]:{port}"
    server.add_insecure_port(listen_addr)
    await server.start()
    logger.info("AI Review gRPC server listening on %s", listen_addr)
    await server.wait_for_termination()
