from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel, Field
from config import settings
from service import rag

router = APIRouter()


def _verify_secret(x_internal_secret: str = Header(default="")) -> None:
    if settings.internal_secret is not None and x_internal_secret != settings.internal_secret:
        raise HTTPException(status_code=403)


class AskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)


class AskResponse(BaseModel):
    answer: str
    sources: list[str]


@router.post("/ask", response_model=AskResponse, dependencies=[Depends(_verify_secret)])
async def ask(request: AskRequest) -> AskResponse:
    answer, sources = await rag.ask(request.question)
    return AskResponse(answer=answer, sources=sources)
