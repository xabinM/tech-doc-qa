from elasticsearch import AsyncElasticsearch
from config import settings

_es = AsyncElasticsearch(settings.es_url)


async def ping() -> bool:
    return await _es.ping()


async def search_docs(question: str) -> list[dict]:
    resp = await _es.search(
        index=settings.es_index,
        body={
            "query": {
                "multi_match": {
                    "query": question,
                    "fields": ["title^2", "content"],
                }
            },
            "size": settings.search_size,
            "_source": ["content", "url"],
        },
    )
    return [hit["_source"] for hit in resp["hits"]["hits"]]
