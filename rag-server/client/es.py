import asyncio
from concurrent.futures import ThreadPoolExecutor
from elasticsearch import AsyncElasticsearch
from sentence_transformers import SentenceTransformer
from config import settings

_es = AsyncElasticsearch(settings.es_url)
_model: SentenceTransformer | None = None
_encoder_pool = ThreadPoolExecutor(max_workers=2, thread_name_prefix="encoder")


async def load_model() -> None:
    global _model
    loop = asyncio.get_running_loop()
    _model = await loop.run_in_executor(_encoder_pool, SentenceTransformer, "all-MiniLM-L6-v2")


async def close_es() -> None:
    await _es.close()


async def ping() -> bool:
    return await _es.ping()


async def search_docs(question: str) -> list[dict]:
    if _model is None:
        raise RuntimeError("임베딩 모델이 초기화되지 않았습니다.")
    embedding = await asyncio.get_running_loop().run_in_executor(_encoder_pool, _model.encode, question)
    embedding = embedding.tolist()

    resp = await _es.search(
        index=settings.es_index,
        body={
            "query": {
                "multi_match": {
                    "query": question,
                    "fields": ["title^2", "content"],
                }
            },
            "knn": {
                "field": "embedding",
                "query_vector": embedding,
                "k": settings.search_size,
                "num_candidates": settings.search_size * 10,
            },
            "size": settings.search_size,
            "_source": ["content", "url"],
        },
    )
    return [hit["_source"] for hit in resp.get("hits", {}).get("hits", [])]
