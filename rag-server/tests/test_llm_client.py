"""
client/llm.py의 _trim_chunks(), generate_answer() 단위 테스트.
Anthropic API 호출은 AsyncMock으로 처리.
"""
import os
import sys
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

os.environ.setdefault("ES_URL", "http://localhost:9200")
os.environ.setdefault("CLAUDE_API_KEY", "test-key")

# anthropic이 설치되지 않은 환경에서도 임포트가 성공하도록
# sys.modules에 stub을 미리 주입한다.
_anthropic_stub = MagicMock()
_anthropic_stub.AsyncAnthropic = MagicMock(return_value=MagicMock())
sys.modules.setdefault("anthropic", _anthropic_stub)

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import asyncio
from client.llm import _trim_chunks, generate_answer, _MAX_CONTEXT_CHARS


def run(coro):
    """동기 테스트 메서드에서 코루틴을 실행하는 헬퍼."""
    loop = asyncio.new_event_loop()
    try:
        return loop.run_until_complete(coro)
    finally:
        loop.close()


# ---------------------------------------------------------------------------
# _trim_chunks 단위 테스트
# ---------------------------------------------------------------------------

class TestTrimChunks(unittest.TestCase):

    def test_빈리스트_첫번째청크보장_빈리스트반환(self):
        # given: chunks[:1]은 빈 리스트를 그대로 반환
        chunks = []

        # when
        result = _trim_chunks(chunks)

        # then: or 우항인 chunks[:1]도 []이므로 빈 리스트
        self.assertEqual([], result)

    def test_단일청크가8000자초과_그청크만반환(self):
        # given: 하나의 청크가 MAX_CONTEXT_CHARS를 초과
        big_chunk = "A" * (_MAX_CONTEXT_CHARS + 1)
        chunks = [big_chunk]

        # when: 루프에서 조건 불만족 → selected=[] → chunks[:1] 반환
        result = _trim_chunks(chunks)

        # then: 초과하더라도 첫 청크는 반드시 반환
        self.assertEqual([big_chunk], result)

    def test_정상절단_합이MAX를넘는순간중단(self):
        # given: 4000 + 4000 = 8000까지는 허용, 세 번째 청크에서 초과
        chunk_a = "A" * 4000
        chunk_b = "B" * 4000
        chunk_c = "C" * 1  # 추가 시 8001자 → 초과
        chunks = [chunk_a, chunk_b, chunk_c]

        # when
        result = _trim_chunks(chunks)

        # then: 첫 두 청크만 선택
        self.assertEqual([chunk_a, chunk_b], result)

    def test_모든청크합이MAX미만_전체반환(self):
        # given: 각 100자 × 5 = 500자, MAX 미만
        chunks = ["X" * 100 for _ in range(5)]

        # when
        result = _trim_chunks(chunks)

        # then: 전체 반환
        self.assertEqual(chunks, result)

    def test_첫청크가정확히8000자_두번째부터제외(self):
        # given: 첫 청크가 MAX_CONTEXT_CHARS와 정확히 같음
        chunk_a = "A" * _MAX_CONTEXT_CHARS  # total=8000, 조건 8000 > 8000은 False → 포함
        chunk_b = "B" * 1                   # total=8001 → 초과
        chunks = [chunk_a, chunk_b]

        # when
        result = _trim_chunks(chunks)

        # then: 첫 청크만 반환
        self.assertEqual([chunk_a], result)

    def test_다중청크_첫번째만MAX초과_첫청크반환(self):
        # given: 첫 청크가 이미 초과, 두 번째 청크는 작음
        big_chunk = "A" * (_MAX_CONTEXT_CHARS + 500)
        small_chunk = "B" * 10
        chunks = [big_chunk, small_chunk]

        # when
        result = _trim_chunks(chunks)

        # then: selected=[] → chunks[:1] = [big_chunk]
        self.assertEqual([big_chunk], result)


# ---------------------------------------------------------------------------
# generate_answer 단위 테스트
# ---------------------------------------------------------------------------

class TestGenerateAnswer(unittest.TestCase):

    def _make_text_response(self, text: str):
        """Anthropic 텍스트 응답 객체를 흉내 내는 MagicMock을 반환한다."""
        content_block = MagicMock()
        content_block.type = "text"
        content_block.text = text
        message = MagicMock()
        message.content = [content_block]
        return message

    def _make_non_text_response(self):
        """type이 'text'가 아닌 응답 객체를 반환한다."""
        content_block = MagicMock()
        content_block.type = "tool_use"
        message = MagicMock()
        message.content = [content_block]
        return message

    def _make_empty_content_response(self):
        """content가 빈 리스트인 응답 객체를 반환한다."""
        message = MagicMock()
        message.content = []
        return message

    # ------------------------------------------------------------------
    # Happy path
    # ------------------------------------------------------------------

    def test_LLM텍스트응답_정상반환(self):
        # given
        mock_response = self._make_text_response("Spring Boot는 자동 설정을 지원합니다.")
        mock_create = AsyncMock(return_value=mock_response)

        with patch("client.llm._client") as mock_client:
            mock_client.messages.create = mock_create

            # when
            result = run(generate_answer("Spring Boot란?", ["관련 내용"]))

        # then
        self.assertEqual("Spring Boot는 자동 설정을 지원합니다.", result)

    def test_LLM호출시_올바른파라미터전달(self):
        # given
        mock_response = self._make_text_response("답변")
        mock_create = AsyncMock(return_value=mock_response)

        with patch("client.llm._client") as mock_client:
            mock_client.messages.create = mock_create

            # when
            run(generate_answer("질문입니다.", ["청크 A", "청크 B"]))

        # then: messages.create 호출 파라미터 검증
        call_kwargs = mock_create.call_args.kwargs
        from config import settings
        self.assertEqual(settings.claude_model, call_kwargs["model"])
        self.assertEqual(1024, call_kwargs["max_tokens"])
        # system에 cache_control이 포함됐는지 확인
        system_block = call_kwargs["system"][0]
        self.assertEqual("text", system_block["type"])
        self.assertIn("cache_control", system_block)
        # 사용자 메시지에 context와 question이 포함됐는지 확인
        user_content = call_kwargs["messages"][0]["content"]
        self.assertIn("청크 A", user_content)
        self.assertIn("청크 B", user_content)
        self.assertIn("질문입니다.", user_content)

    # ------------------------------------------------------------------
    # Edge case: 비텍스트 응답
    # ------------------------------------------------------------------

    def test_LLM비텍스트응답_ValueError발생(self):
        # given
        mock_response = self._make_non_text_response()
        mock_create = AsyncMock(return_value=mock_response)

        with patch("client.llm._client") as mock_client:
            mock_client.messages.create = mock_create

            # when / then
            with self.assertRaises(ValueError) as ctx:
                run(generate_answer("질문", ["내용"]))

        self.assertIn("텍스트 응답", str(ctx.exception))

    def test_LLM빈content응답_ValueError발생(self):
        # given
        mock_response = self._make_empty_content_response()
        mock_create = AsyncMock(return_value=mock_response)

        with patch("client.llm._client") as mock_client:
            mock_client.messages.create = mock_create

            # when / then
            with self.assertRaises(ValueError):
                run(generate_answer("질문", ["내용"]))

    # ------------------------------------------------------------------
    # Edge case: _trim_chunks 연동 — 큰 청크는 절단 후 전달
    # ------------------------------------------------------------------

    def test_대용량청크_trim후LLM호출(self):
        # given: MAX_CONTEXT_CHARS를 초과하는 두 개의 청크
        big_chunk_a = "A" * 5000
        big_chunk_b = "B" * 5000  # 합산 10000 > 8000 → 두 번째는 잘림
        mock_response = self._make_text_response("답변")
        mock_create = AsyncMock(return_value=mock_response)

        with patch("client.llm._client") as mock_client:
            mock_client.messages.create = mock_create

            # when
            run(generate_answer("질문", [big_chunk_a, big_chunk_b]))

        # then: 두 번째 청크가 제외됐으므로 context에 'B'가 없어야 함
        call_kwargs = mock_create.call_args.kwargs
        user_content = call_kwargs["messages"][0]["content"]
        self.assertIn("A" * 5000, user_content)
        self.assertNotIn("B" * 5000, user_content)


if __name__ == "__main__":
    unittest.main()
