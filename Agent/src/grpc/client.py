import grpc
import os
import src.grpc.usage_pb2 as usage_pb2
import src.grpc.usage_pb2_grpc as usage_pb2_grpc
import src.grpc.ai_agent_review_pb2 as ai_pb2
import src.grpc.ai_agent_review_pb2_grpc as ai_pb2_grpc
from src.models import DomainUsageItem, UsageAnalysis, BlockingRecommendation
from typing import List

class UsageClient:
    def __init__(self, host=None, port=None):
        host = host or os.getenv("USAGE_GRPC_HOST", "localhost")
        port = int(port or os.getenv("USAGE_GRPC_PORT", "50051"))
        self.channel = grpc.insecure_channel(f'{host}:{port}')
        self.stub = usage_pb2_grpc.UsageServiceStub(self.channel)

    def get_usage_data(self, period: str) -> List[DomainUsageItem]:
        try:
            request = usage_pb2.UsageRequest(period=period)
            response = self.stub.GetUsage(request)
            
            return [
                DomainUsageItem(
                    domain=item.domain,
                    duration_seconds=item.duration_seconds,
                    last_accessed=item.last_accessed
                )
                for item in response.usage_list
            ]
        except grpc.RpcError as e:
            # For now, return a mock/dummy list if the server isn't running for testing
            print(f"gRPC error: {e.code()} - {e.details()}")
            return self._get_mock_data(period)

    def _get_mock_data(self, period: str) -> List[DomainUsageItem]:
        # Mock data for YouTube addiction simulation
        return [
            DomainUsageItem(domain="youtube.com", duration_seconds=14400, last_accessed="2024-03-08T18:00:00Z"),
            DomainUsageItem(domain="google.com", duration_seconds=1200, last_accessed="2024-03-08T19:00:00Z"),
            DomainUsageItem(domain="github.com", duration_seconds=3600, last_accessed="2024-03-08T20:00:00Z"),
            DomainUsageItem(domain="tiktok.com", duration_seconds=7200, last_accessed="2024-03-08T15:00:00Z"),
        ]

class ReviewClient:
    def __init__(self, host=None, port=None):
        host = host or os.getenv("REVIEW_GRPC_HOST", "localhost")
        port = int(port or os.getenv("REVIEW_GRPC_PORT", "50052"))
        self.channel = grpc.insecure_channel(f'{host}:{port}')
        self.stub = ai_pb2_grpc.AiAgentReviewServiceStub(self.channel)

    def analyze_usage(self, user_id: str, period: str, usage_items: List[DomainUsageItem]) -> UsageAnalysis:
        # Convert DomainUsageItem to DomainUsage proto
        top_domains = [
            ai_pb2.DomainUsage(
                domain=item.domain,
                total_duration=item.duration_seconds // 60, # Back to minutes for proto
                request_count=1 # Mock count
            )
            for item in usage_items
        ]

        usage_dto = ai_pb2.UsageDto(
            user_id=user_id,
            period=period,
            total_queries=sum(len(usage_items) for _ in usage_items), # Mock queries
            unique_domains=len(usage_items),
            top_domains=top_domains
        )

        request = ai_pb2.ReviewRequest(
            session_id=f"{user_id}_{period}",
            usage=usage_dto
        )

        try:
            response = self.stub.AnalyzeUsage(request)
            return UsageAnalysis(
                is_addicted=response.is_addicted,
                risk_level=response.risk_level,
                summary=response.summary,
                recommendation=response.recommendation,
                addictive_domains=list(response.addictive_domains),
                blocking_recommendations=[
                    BlockingRecommendation(
                        domain=br.domain,
                        reason=br.reason,
                        recommended_limit_minutes=br.recommended_limit_minutes
                    )
                    for br in response.blocking_recommendations
                ]
            )
        except grpc.RpcError as e:
            print(f"gRPC error: {e.code()} - {e.details()}")
            raise
