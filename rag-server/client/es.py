from elasticsearch import AsyncElasticsearch
from sentence_transformers import SentenceTransformer
from config import settings

_es = AsyncElasticsearch(settings.es_url)
_model = SentenceTransformer("all-MiniLM-L6-v2")


async def ping() -> bool:
    return await _es.ping()


async def search_docs(question: str) -> list[dict]:
    embedding = _model.encode(question).tolist()

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
    return [hit["_source"] for hit in resp["hits"]["hits"]]
