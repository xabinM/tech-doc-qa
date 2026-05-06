from fastapi import APIRouter
from pydantic import BaseModel
from service import rag

router = APIRouter()


class AskRequest(BaseModel):
    question: str


class AskResponse(BaseModel):
    answer: str


@router.post("/ask", response_model=AskResponse)
async def ask(request: AskRequest) -> AskResponse:
    answer = await rag.ask(request.question)
    return AskResponse(answer=answer)
