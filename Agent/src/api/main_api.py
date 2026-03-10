from fastapi import FastAPI, HTTPException
from src.grpc.client import UsageClient, ReviewClient
from src.models import UsageAnalysis

app = FastAPI(
    title="Detox AI Monitoring API",
    description="Analyzes domain usage data via gRPC and provides AI-powered detox recommendations.",
    version="1.0.0"
)

usage_grpc_client = UsageClient()
review_grpc_client = ReviewClient()

@app.get("/")
async def root():
    return {"message": "Detox AI Monitoring Agent is running."}

@app.get("/analyze/{period}", response_model=UsageAnalysis)
async def analyze_usage(period: str, user_id: str = "default_user"):
    if period not in ["daily", "weekly", "monthly"]:
        raise HTTPException(status_code=400, detail="Invalid period. Use 'daily', 'weekly', or 'monthly'.")

    # 1. Fetch data from gRPC Service (Usage)
    usage_data = usage_grpc_client.get_usage_data(period)
    
    # 2. Run AI Analysis via gRPC Service (AI Review)
    try:
        # We now call the AI Review service via gRPC instead of direct function call
        analysis_result = review_grpc_client.analyze_usage(user_id, period, usage_data)
        return analysis_result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"AI Analysis failed: {str(e)}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
