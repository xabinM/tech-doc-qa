from client import es
from client import llm

_NO_DOCS_ANSWER = "관련 문서를 찾을 수 없습니다."


async def ask(question: str) -> tuple[str, list[str]]:
    docs = await es.search_docs(question)
    if not docs:
        return _NO_DOCS_ANSWER, []
    chunks = [doc["content"] for doc in docs if "content" in doc]
    sources = list(dict.fromkeys(doc["url"] for doc in docs if "url" in doc))
    if not chunks:
        return _NO_DOCS_ANSWER, []
    answer = await llm.generate_answer(question, chunks)
    return answer, sources
