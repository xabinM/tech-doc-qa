import anthropic
from config import settings

_client = anthropic.AsyncAnthropic(api_key=settings.claude_api_key)

_SYSTEM_PROMPT = (
    "You are a technical assistant specializing in Spring and Java documentation. "
    "Answer questions based only on the provided context. "
    "If the context does not contain enough information, say so clearly. "
    "Answer in the same language as the question."
)


async def generate_answer(question: str, chunks: list[str]) -> str:
    context = "\n\n---\n\n".join(chunks)
    message = await _client.messages.create(
        model=settings.claude_model,
        max_tokens=1024,
        system=[{
            "type": "text",
            "text": _SYSTEM_PROMPT,
            "cache_control": {"type": "ephemeral"},
        }],
        messages=[
            {"role": "user", "content": f"Context:\n{context}\n\nQuestion: {question}"}
        ],
    )
    return message.content[0].text
