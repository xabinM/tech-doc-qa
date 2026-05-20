from groq import AsyncGroq
from config import settings

_client: AsyncGroq | None = None

_SYSTEM_PROMPT = (
    "You are a technical assistant specializing in Spring and Java documentation. "
    "Answer questions based only on the provided context. "
    "If the context does not contain enough information, say so clearly. "
    "Answer in the same language as the question."
)

# 청크 400단어 × 5개 ≈ 2000단어 ≈ ~2500토큰, 여유분 포함해 8000자로 제한
_MAX_CONTEXT_CHARS = 8000


def init_llm() -> None:
    global _client
    _client = AsyncGroq(api_key=settings.groq_api_key)


def _trim_chunks(chunks: list[str]) -> list[str]:
    selected, total = [], 0
    for chunk in chunks:
        if total + len(chunk) > _MAX_CONTEXT_CHARS:
            break
        selected.append(chunk)
        total += len(chunk)
    return selected or chunks[:1]


async def generate_answer(question: str, chunks: list[str], history: list[dict] | None = None) -> str:
    if _client is None:
        raise RuntimeError("LLM 클라이언트가 초기화되지 않았습니다.")

    context = "\n\n---\n\n".join(_trim_chunks(chunks))

    messages: list[dict] = [{"role": "system", "content": _SYSTEM_PROMPT}]

    for turn in (history or []):
        messages.append({"role": "user", "content": turn["question"]})
        messages.append({"role": "assistant", "content": turn["answer"]})

    messages.append({
        "role": "user",
        "content": f"<context>\n{context}\n</context>\n\n<question>\n{question}\n</question>"
    })

    response = await _client.chat.completions.create(
        model=settings.groq_model,
        max_tokens=1024,
        messages=messages,
        timeout=60.0,
    )
    if not response.choices:
        raise ValueError("LLM이 응답을 반환하지 않았습니다.")
    answer = response.choices[0].message.content
    if not answer:
        raise ValueError("LLM이 텍스트 응답을 반환하지 않았습니다.")
    return answer
