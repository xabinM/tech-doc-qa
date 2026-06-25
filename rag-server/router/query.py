import json

from fastapi import APIRouter, Depends, Header, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from config import settings
from service import rag

router = APIRouter()


def _sse(event: str, data: str) -> str:
    # SSE 규격: 멀티라인 data는 각 줄마다 'data:' 접두사가 필요하다
    payload = "".join(f"data: {line}\n" for line in data.split("\n"))
    return f"event: {event}\n{payload}\n"


def _verify_secret(x_internal_secret: str = Header(default="")) -> None:
    if settings.internal_secret is not None and x_internal_secret != settings.internal_secret:
        raise HTTPException(status_code=403)


class HistoryItem(BaseModel):
    question: str = Field(max_length=1000)
    answer: str = Field(max_length=5000)


class AskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    history: list[HistoryItem] = Field(default=[], max_length=10)


class AskResponse(BaseModel):
    answer: str
    sources: list[str]


@router.post("/ask", response_model=AskResponse, dependencies=[Depends(_verify_secret)])
async def ask(request: AskRequest) -> AskResponse:
    history = [{"question": h.question, "answer": h.answer} for h in request.history]
    answer, sources = await rag.ask(request.question, history)
    return AskResponse(answer=answer, sources=sources)


@router.post("/ask/stream", dependencies=[Depends(_verify_secret)])
async def ask_stream(request: AskRequest) -> StreamingResponse:
    history = [{"question": h.question, "answer": h.answer} for h in request.history]

    async def event_generator():
        # 스트리밍 시작(200) 후의 예외는 전역 핸들러가 못 잡으므로 여기서 error 이벤트로 전달
        try:
            async for event_type, payload in rag.ask_stream(request.question, history):
                if event_type == "token":
                    yield _sse("token", payload)
                else:  # done
                    yield _sse("done", json.dumps({"sources": payload}, ensure_ascii=False))
        except Exception as exc:
            yield _sse("error", str(exc))

    return StreamingResponse(event_generator(), media_type="text/event-stream")
