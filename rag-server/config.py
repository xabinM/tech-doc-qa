from typing import Optional
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    es_url: str
    es_index: str = "tech-docs"
    claude_api_key: str
    claude_model: str = "claude-sonnet-4-6"
    search_size: int = 5
    internal_secret: Optional[str] = None  # 미설정 시 검증 스킵 (개발 전용)

    model_config = {"env_file": ".env"}


settings = Settings()
