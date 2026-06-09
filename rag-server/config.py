from typing import Optional
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    es_url: str
    es_index: str = "tech-docs"
    groq_api_key: str
    groq_model: str = "llama-3.1-8b-instant"
    search_size: int = 5
    internal_secret: Optional[str] = None  # 미설정 시 검증 스킵 (개발 전용)
    mock_delay_seconds: float = 5.0        # 부하 테스트용 mock 지연 시간 (초)
    enable_mock_endpoint: bool = False     # True일 때만 /ask-mock 등록 (부하 테스트 전용)

    model_config = {"env_file": ".env", "extra": "ignore"}


settings = Settings()
