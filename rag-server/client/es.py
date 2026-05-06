from elasticsearch import AsyncElasticsearch
from config import settings

_es = AsyncElasticsearch(settings.es_url)


async def search_docs(question: str) -> list[str]:
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
            "_source": ["content"],
        },
    )
    return [hit["_source"]["content"] for hit in resp["hits"]["hits"]]
