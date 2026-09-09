from fastapi.testclient import TestClient

from app.main import app


def test_health_and_openapi_contract():
    with TestClient(app) as client:
        response = client.get("/health")
        assert response.status_code == 200
        assert response.json() == {"status": "UP", "service": "seed-ai", "mode": "mock"}
        schema = client.get("/openapi.json").json()
        assert "/health" in schema["paths"]
        assert client.get("/internal/v1/draft-generations").status_code == 404
