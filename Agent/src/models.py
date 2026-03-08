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
class UsageAnalysis(BaseModel):
    is_addicted: bool = Field(description="True if the usage pattern indicates potential digital addiction.")
    risk_level: Literal["Low", "Medium", "High", "Critical"]
    addictive_domains: List[str] = Field(description="List of domains causing the most concern.")
    summary: str = Field(description="A concise summary of the usage analysis.")
    recommendation: str = Field(description="A personalized 'detox' recommendation or nudge.")
    suggested_limit_seconds: Optional[int] = Field(description="Optional suggested daily limit for the top offending domain.")
