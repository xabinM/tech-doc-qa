from client.es import search_docs
from client.llm import generate_answer

_NO_DOCS_ANSWER = "관련 문서를 찾을 수 없습니다."


async def ask(question: str) -> str:
    chunks = await search_docs(question)
    if not chunks:
        return _NO_DOCS_ANSWER
    return await generate_answer(question, chunks)
