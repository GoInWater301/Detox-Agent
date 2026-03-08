import os
from dotenv import load_dotenv
from pydantic_ai import Agent, RunContext
from src.models import DomainUsageItem, UsageAnalysis
from typing import List

load_dotenv()

# Expert AI Agent for Analyzing Domain Usage
# Persona: "Compassionate Digital Health Coach"
usage_analyst_agent = Agent(
    'openai:gpt-4o',  # or 'google-g2:gemini-1.5-pro'
    result_type=UsageAnalysis,
    system_prompt=(
        "You are an expert Digital Health & Productivity coach. Your task is to analyze a user's domain usage data "
        "(domain name, duration in seconds, frequency). "
        "1. Identify Red Flags: Look for domains like youtube.com, tiktok.com, instagram.com or gaming sites "
        "with duration exceeding 2 hours/day.\n"
        "2. Analyze Trends: Determine if the usage reflects an addictive pattern or healthy research/productivity.\n"
        "3. Your Final Response should be constructive but firm if addiction is detected. "
        "Provide a firm, slightly discouraging (yet helpful) response when addictive behaviors are identified. "
        "Suggest specific alternatives (e.g., 'instead of 4 hours on YouTube, you could have read 50 pages of a book')."
    ),
)

@usage_analyst_agent.tool
async def calculate_total_time(ctx: RunContext[List[DomainUsageItem]]) -> str:
    """Helper tool to calculate total screentime from the data provided."""
    total_seconds = sum(item.duration_seconds for item in ctx.deps)
    hours = total_seconds // 3600
    minutes = (total_seconds % 3600) // 60
    return f"Total Usage Time: {hours}h {minutes}m"

async def run_analysis(usage_list: List[DomainUsageItem]) -> UsageAnalysis:
    """Run the AI analysis on a list of domain usage items."""
    result = await usage_analyst_agent.run(
        f"Analyze this domain usage data: {usage_list}",
        deps=usage_list
    )
    return result.data
