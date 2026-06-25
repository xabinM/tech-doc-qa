from client import es
from client import llm

_NO_DOCS_ANSWER = "관련 문서를 찾을 수 없습니다."


async def ask(question: str, history: list[dict] | None = None) -> tuple[str, list[str]]:
    docs = await es.search_docs(question)
    if not docs:
        return _NO_DOCS_ANSWER, []
    chunks = [doc["content"] for doc in docs if "content" in doc]
    sources = list(dict.fromkeys(doc["url"] for doc in docs if "url" in doc))
    if not chunks:
        return _NO_DOCS_ANSWER, []
    answer = await llm.generate_answer(question, chunks, history or [])
    return answer, sources


async def ask_stream(question: str, history: list[dict] | None = None):
    """
    답변을 스트리밍한다. (event_type, payload) 튜플을 순차 yield 한다.
      ("token", <텍스트 조각>) ... ("done", <sources 리스트>)
    문서를 찾지 못하면 안내 문구를 토큰으로 한 번 보내고 종료한다.
    """
    docs = await es.search_docs(question)
    chunks = [doc["content"] for doc in docs if "content" in doc] if docs else []
    if not chunks:
        yield ("token", _NO_DOCS_ANSWER)
        yield ("done", [])
        return

    sources = list(dict.fromkeys(doc["url"] for doc in docs if "url" in doc))
    async for token in llm.generate_answer_stream(question, chunks, history or []):
        yield ("token", token)
    yield ("done", sources)
