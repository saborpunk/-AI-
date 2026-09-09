from typing import Literal

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="Seed Assistant Mock Service", version="0.1.0")


class HealthResponse(BaseModel):
    status: Literal["UP"] = "UP"
    service: Literal["seed-ai"] = "seed-ai"
    mode: Literal["mock"] = "mock"


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    # 只表示本服务能响应，不代表真实模型可用或种植结论已核实。
    return HealthResponse()
