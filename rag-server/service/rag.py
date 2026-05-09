from client.es import search_docs
from client.llm import generate_answer

_NO_DOCS_ANSWER = "관련 문서를 찾을 수 없습니다."


async def ask(question: str) -> tuple[str, list[str]]:
    docs = await search_docs(question)
    if not docs:
        return _NO_DOCS_ANSWER, []
    chunks = [doc["content"] for doc in docs]
    sources = list(dict.fromkeys(doc["url"] for doc in docs))
    answer = await generate_answer(question, chunks)
    return answer, sources
