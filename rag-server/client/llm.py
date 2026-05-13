import anthropic
from config import settings

_client = anthropic.AsyncAnthropic(api_key=settings.claude_api_key)

_SYSTEM_PROMPT = (
    "You are a technical assistant specializing in Spring and Java documentation. "
    "Answer questions based only on the provided context. "
    "If the context does not contain enough information, say so clearly. "
    "Answer in the same language as the question."
)


_MAX_CONTEXT_CHARS = 8000


def _trim_chunks(chunks: list[str]) -> list[str]:
    selected, total = [], 0
    for chunk in chunks:
        if total + len(chunk) > _MAX_CONTEXT_CHARS:
            break
        selected.append(chunk)
        total += len(chunk)
    return selected or chunks[:1]


async def generate_answer(question: str, chunks: list[str]) -> str:
    context = "\n\n---\n\n".join(_trim_chunks(chunks))
    message = await _client.messages.create(
        model=settings.claude_model,
        max_tokens=1024,
        system=[{
            "type": "text",
            "text": _SYSTEM_PROMPT,
            "cache_control": {"type": "ephemeral"},
        }],
        messages=[
            {"role": "user", "content": f"<context>\n{context}\n</context>\n\n<question>\n{question}\n</question>"}
        ],
    )
    if not message.content or message.content[0].type != "text":
        raise ValueError("LLM이 텍스트 응답을 반환하지 않았습니다.")
    return message.content[0].text
