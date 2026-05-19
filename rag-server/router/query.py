from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel, Field
from config import settings
from service import rag

router = APIRouter()


def _verify_secret(x_internal_secret: str = Header(default="")) -> None:
    if settings.internal_secret is not None and x_internal_secret != settings.internal_secret:
        raise HTTPException(status_code=403)


class HistoryItem(BaseModel):
    question: str
    answer: str


class AskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    history: list[HistoryItem] = []


class AskResponse(BaseModel):
    answer: str
    sources: list[str]


@router.post("/ask", response_model=AskResponse, dependencies=[Depends(_verify_secret)])
async def ask(request: AskRequest) -> AskResponse:
    history = [{"question": h.question, "answer": h.answer} for h in request.history]
    answer, sources = await rag.ask(request.question, history)
    return AskResponse(answer=answer, sources=sources)
