import grpc
import os
import src.grpc.usage_pb2 as usage_pb2
import src.grpc.usage_pb2_grpc as usage_pb2_grpc
from src.models import DomainUsageItem
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
