from fastapi import FastAPI, HTTPException
from src.grpc.client import UsageClient
from src.ai.agent import run_analysis
from src.models import UsageAnalysis

app = FastAPI(
    title="Detox AI Monitoring API",
    description="Analyzes domain usage data via gRPC and provides AI-powered detox recommendations.",
    version="1.0.0"
)

grpc_client = UsageClient()

@app.get("/")
async def root():
    return {"message": "Detox AI Monitoring Agent is running."}

@app.get("/analyze/{period}", response_model=UsageAnalysis)
async def analyze_usage(period: str):
    if period not in ["daily", "weekly", "monthly"]:
        raise HTTPException(status_code=400, detail="Invalid period. Use 'daily', 'weekly', or 'monthly'.")

    # 1. Fetch data from gRPC Service
    usage_data = grpc_client.get_usage_data(period)
    
    # 2. Run AI Analysis
    try:
        analysis_result = await run_analysis(usage_data)
        return analysis_result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"AI Analysis failed: {str(e)}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
