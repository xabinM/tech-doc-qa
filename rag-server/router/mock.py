import asyncio
from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from config import settings
from router.query import _verify_secret

router = APIRouter()


class MockAskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    history: list = Field(default=[])


class MockAskResponse(BaseModel):
    answer: str
    sources: list[str]


@router.post("/ask-mock", response_model=MockAskResponse,
             dependencies=[Depends(_verify_secret)])
async def ask_mock(request: MockAskRequest) -> MockAskResponse:
    """부하 테스트 전용 mock 엔드포인트.
    실제 ES/LLM 호출 없이 MOCK_DELAY_SECONDS 만큼 대기 후 응답.
    스레드 포화, 가상 스레드 효과 측정에 사용한다.
    """
    await asyncio.sleep(settings.mock_delay_seconds)
    return MockAskResponse(
        answer=f"[MOCK] {request.question[:80]} (지연 {settings.mock_delay_seconds}s)",
        sources=[],
    )
