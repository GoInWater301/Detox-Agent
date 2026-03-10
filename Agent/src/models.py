from typing import List, Literal, Optional
from pydantic import BaseModel, Field

# Models for gRPC Input Data (Matched from usage.proto)
class DomainUsageItem(BaseModel):
    domain: str
    duration_seconds: int
    last_accessed: str

class UsageRequest(BaseModel):
    period: Literal["daily", "weekly", "monthly"]

# Models for AI Output (Structured Feedback)
class BlockingRecommendation(BaseModel):
    domain: str = Field(description="차단 또는 제한을 권장하는 도메인입니다.")
    reason: str = Field(description="해당 도메인을 차단/제한해야 하는 이유입니다.")
    recommended_limit_minutes: Optional[int] = Field(description="권장되는 일일 사용 제한 시간(분)입니다. 완전히 차단해야 한다면 0입니다.")

class UsageAnalysis(BaseModel):
    is_addicted: bool = Field(description="사용 패턴이 디지털 중독 가능성을 나타내면 True입니다.")
    risk_level: Literal["Low", "Medium", "High", "Critical"]
    addictive_domains: List[str] = Field(description="가장 우려되는 도메인 목록입니다.")
    summary: str = Field(description="사용량 분석에 대한 간결한 요약입니다.")
    recommendation: str = Field(description="개인화된 '디톡스' 권장 사항 또는 넛지입니다.")
    blocking_recommendations: List[BlockingRecommendation] = Field(default_factory=list, description="구체적인 차단 또는 제한 추천 리스트입니다.")
    suggested_limit_seconds: Optional[int] = Field(description="문제가 되는 상위 도메인에 대한 권장 일일 제한 시간(초)입니다.")
