import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
import anthropic
from elasticsearch import exceptions as es_exc
from client.es import ping, load_model, close_es
from client.llm import init_llm
from config import settings
from router.query import router

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    if settings.internal_secret is None:
        logger.warning("INTERNAL_SECRET이 설정되지 않았습니다. /ask 인증이 비활성화된 상태입니다.")
    init_llm()
    await load_model()
    yield
    await close_es()


app = FastAPI(title="RAG Server", lifespan=lifespan)
app.include_router(router)


@app.get("/health")
async def health():
    if not await ping():
        return JSONResponse(
            status_code=503,
            content={"status": "unhealthy", "detail": "Elasticsearch에 연결할 수 없습니다."},
        )
    return {"status": "ok"}


@app.exception_handler(RequestValidationError)
async def validation_error_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(status_code=400, content={"detail": "잘못된 요청입니다."})


@app.exception_handler(es_exc.TransportError)
async def es_error_handler(request: Request, exc: es_exc.TransportError):
    return JSONResponse(status_code=503, content={"detail": "Elasticsearch 오류가 발생했습니다."})


@app.exception_handler(anthropic.APIError)
async def anthropic_error_handler(request: Request, exc: anthropic.APIError):
    return JSONResponse(status_code=503, content={"detail": "LLM API 오류가 발생했습니다."})


@app.exception_handler(ValueError)
async def value_error_handler(request: Request, exc: ValueError):
    return JSONResponse(status_code=502, content={"detail": str(exc)})


@app.exception_handler(Exception)
async def general_error_handler(request: Request, exc: Exception):
    return JSONResponse(status_code=500, content={"detail": "서버 내부 오류가 발생했습니다."})
