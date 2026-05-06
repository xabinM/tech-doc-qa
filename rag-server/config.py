from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    es_url: str
    es_index: str = "tech-docs"
    claude_api_key: str
    claude_model: str = "claude-sonnet-4-6"
    search_size: int = 5

    model_config = {"env_file": ".env"}


settings = Settings()
