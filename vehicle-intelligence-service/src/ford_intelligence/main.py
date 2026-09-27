import logging

import structlog
from fastapi import FastAPI, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from ford_intelligence.api.routes import router
from ford_intelligence.application.services import IntelligenceService
from ford_intelligence.config import get_settings
from ford_intelligence.infrastructure.repositories import (
    MongoTelemetryRepository,
    RedisRecommendationCache,
)
from ford_intelligence.pipeline.telemetry import TelemetryPipeline


def create_app(service: IntelligenceService | None = None) -> FastAPI:
    settings = get_settings()
    logging.basicConfig(level=settings.log_level)
    structlog.configure(
        processors=[
            structlog.processors.TimeStamper(fmt="iso"),
            structlog.processors.JSONRenderer(),
        ]
    )
    app = FastAPI(
        title=settings.app_name,
        version="1.0.0",
        description="Vehicle evaluation and maintenance recommendations by business rules.",
    )
    app.state.intelligence_service = service or IntelligenceService(
        MongoTelemetryRepository(settings.mongo_url),
        RedisRecommendationCache(settings.redis_url),
        TelemetryPipeline(),
    )
    app.include_router(router)

    @app.get("/health", tags=["operations"])
    def health() -> dict[str, str]:
        return {"status": "UP"}

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, error: RequestValidationError) -> JSONResponse:
        errors = jsonable_encoder(error.errors())
        structlog.get_logger().warning(
            "request_validation_failed", path=request.url.path, validation_errors=errors
        )
        return JSONResponse(status_code=422, content={"detail": errors})

    @app.exception_handler(Exception)
    async def unhandled_error(request: Request, error: Exception) -> JSONResponse:
        structlog.get_logger().exception(
            "unhandled_error", path=request.url.path, error_type=type(error).__name__
        )
        return JSONResponse(
            status_code=500,
            content={"type": "internal_error", "title": "Unexpected error", "status": 500},
        )

    return app


app = create_app()
