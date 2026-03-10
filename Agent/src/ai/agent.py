import os
from dotenv import load_dotenv
from pydantic_ai import Agent, RunContext
from pydantic_ai.models.test import TestModel
from src.models import DomainUsageItem, UsageAnalysis
from typing import List

load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

# Expert AI Agent for Analyzing Domain Usage
# Persona: "Compassionate Digital Health Coach"
if OPENAI_API_KEY and not OPENAI_API_KEY.startswith("your_"):
    model = 'openai:gpt-4o'
else:
    # Use TestModel if API key is missing to avoid crash on startup
    model = TestModel()

usage_analyst_agent = Agent(
    model,
    output_type=UsageAnalysis,
    system_prompt=(
        "당신은 디지털 헬스 및 생산성 코치 전문가입니다. 당신의 임무는 사용자의 도메인 사용 데이터"
        "(도메인 이름, 사용 시간(분), 요청 횟수)를 분석하는 것입니다.\n"
        "1. 위험 신호 식별: youtube.com, tiktok.com, instagram.com 또는 게임 사이트와 같이 "
        "사용량이 비정상적으로 높거나 업무 외적인 시간이 과도한 도메인을 찾으세요.\n"
        "2. 패턴 분석: 사용 패턴이 중독적인 성향을 보이는지, 아니면 건강한 조사/생산성 활동인지 판단하세요.\n"
        "3. 차단 추천: 중독적 사용이 감지된 도메인에 대해 구체적인 '차단 추천 리스트(blocking_recommendations)'를 작성하세요. "
        "각 추천 항목에는 도메인 이름, 차단/제한 사유, 권장 일일 제한 시간(분)을 포함해야 합니다. 완전히 차단을 원할 경우 0분을 제안하세요.\n"
        "4. 최종 응답: 중독적 행동이 감지되면 건설적이면서도 단호하게 응답하세요. "
        "구체적인 대안을 제시하세요 (예: '유튜브에서 4시간을 보내는 대신, 책 50페이지를 읽을 수 있었을 것입니다')."
    ),
)

@usage_analyst_agent.tool
async def calculate_total_time(ctx: RunContext[List[DomainUsageItem]]) -> str:
    """제공된 데이터에서 총 화면 사용 시간을 계산하는 도우미 도구입니다."""
    total_seconds = sum(item.duration_seconds for item in ctx.deps)
    hours = total_seconds // 3600
    minutes = (total_seconds % 3600) // 60
    return f"총 사용 시간: {hours}시간 {minutes}분"

async def run_analysis(usage_list: List[DomainUsageItem]) -> UsageAnalysis:
    """Run the AI analysis on a list of domain usage items."""
    if isinstance(usage_analyst_agent.model, TestModel):
        return UsageAnalysis(
            is_addicted=False,
            risk_level="Low",
            addictive_domains=[],
            summary="[MOCK] OpenAI API 키가 설정되지 않았습니다. .env 파일을 확인해주세요.",
            recommendation="API 키를 설정하면 실제 AI 분석 결과를 확인할 수 있습니다.",
            suggested_limit_seconds=0
        )
    
    result = await usage_analyst_agent.run(
        f"Analyze this domain usage data: {usage_list}",
        deps=usage_list
    )
    return getattr(result, "output", getattr(result, "data"))
