from fastapi import APIRouter
from pydantic import BaseModel, Field
from service import rag

router = APIRouter()


class AskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)


class AskResponse(BaseModel):
    answer: str
    sources: list[str]


@router.post("/ask", response_model=AskResponse)
async def ask(request: AskRequest) -> AskResponse:
    answer, sources = await rag.ask(request.question)
    return AskResponse(answer=answer, sources=sources)
