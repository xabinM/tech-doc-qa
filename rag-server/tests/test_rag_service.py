"""
service/rag.py의 ask() 함수 단위 테스트.
외부 의존성(search_docs, generate_answer)은 AsyncMock으로 처리.
"""
import os
import sys
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

os.environ.setdefault("ES_URL", "http://localhost:9200")
os.environ.setdefault("CLAUDE_API_KEY", "test-key")

sys.modules.setdefault("elasticsearch", MagicMock())
sys.modules.setdefault("sentence_transformers", MagicMock())

_es_stub = MagicMock()
_es_stub.search_docs = AsyncMock(return_value=[])
sys.modules["client.es"] = _es_stub

_anthropic_stub = MagicMock()
_anthropic_stub.AsyncAnthropic = MagicMock(return_value=MagicMock())
sys.modules.setdefault("anthropic", _anthropic_stub)

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import asyncio

# patch.object(rag_module, ...) 를 사용해야 ask.__globals__와 동일한
# 모듈 객체를 패치할 수 있다. 문자열 경로 patch는 sys.modules["service.rag"]를
# 패치하지만, 다른 테스트가 service.rag를 재임포트했다면 ask와 다른 객체가 된다.
sys.modules.pop("service.rag", None)
import service.rag as rag_module


def run(coro):
    """동기 테스트 메서드에서 코루틴을 실행하는 헬퍼."""
    loop = asyncio.new_event_loop()
    try:
        return loop.run_until_complete(coro)
    finally:
        loop.close()


class TestRagAsk(unittest.TestCase):

    # ------------------------------------------------------------------
    # Edge case: ES 결과 없음
    # ------------------------------------------------------------------

    def test_ES결과없음_NO_DOCS_ANSWER와_빈sources반환(self):
        # given
        mock_gen = AsyncMock()
        with (
            patch.object(rag_module, "search_docs", new=AsyncMock(return_value=[])),
            patch.object(rag_module, "generate_answer", mock_gen),
        ):
            # when
            answer, sources = run(rag_module.ask("Spring이란?"))

        # then
        self.assertEqual("관련 문서를 찾을 수 없습니다.", answer)
        self.assertEqual([], sources)
        mock_gen.assert_not_called()

    # ------------------------------------------------------------------
    # Edge case: content 키 없는 doc
    # ------------------------------------------------------------------

    def test_content키없는doc_필터링후chunks없음_NO_DOCS_ANSWER반환(self):
        # given: url만 있고 content가 없는 문서
        docs = [
            {"url": "https://example.com/a"},
            {"url": "https://example.com/b"},
        ]
        mock_gen = AsyncMock()
        with (
            patch.object(rag_module, "search_docs", new=AsyncMock(return_value=docs)),
            patch.object(rag_module, "generate_answer", mock_gen),
        ):
            # when
            answer, sources = run(rag_module.ask("질문"))

        # then: content가 하나도 없으면 LLM 호출 없이 NO_DOCS_ANSWER 반환
        self.assertEqual("관련 문서를 찾을 수 없습니다.", answer)
        self.assertEqual([], sources)
        mock_gen.assert_not_called()

    def test_일부doc만content키보유_content있는doc만LLM전달(self):
        # given: 첫 번째 doc에만 content 있음
        docs = [
            {"content": "Spring은 Java 프레임워크입니다.", "url": "https://spring.io/a"},
            {"url": "https://spring.io/b"},  # content 없음 → 필터링
        ]
        mock_gen = AsyncMock(return_value="생성된 답변")
        with (
            patch.object(rag_module, "search_docs", new=AsyncMock(return_value=docs)),
            patch.object(rag_module, "generate_answer", mock_gen),
        ):
            # when
            answer, sources = run(rag_module.ask("질문"))

        # then
        self.assertEqual("생성된 답변", answer)
        call_chunks = mock_gen.call_args[0][1]
        self.assertEqual(["Spring은 Java 프레임워크입니다."], call_chunks)

    # ------------------------------------------------------------------
    # Edge case: url 중복
    # ------------------------------------------------------------------

    def test_URL중복_중복제거된sources반환(self):
        # given: 동일 URL을 가진 여러 doc
        docs = [
            {"content": "내용 A", "url": "https://spring.io/page"},
            {"content": "내용 B", "url": "https://spring.io/page"},  # 중복
            {"content": "내용 C", "url": "https://docs.spring.io/other"},
        ]
        mock_gen = AsyncMock(return_value="답변")
        with (
            patch.object(rag_module, "search_docs", new=AsyncMock(return_value=docs)),
            patch.object(rag_module, "generate_answer", mock_gen),
        ):
            # when
            answer, sources = run(rag_module.ask("질문"))

        # then: URL 순서 유지하며 중복 제거
        self.assertEqual(["https://spring.io/page", "https://docs.spring.io/other"], sources)

    def test_URL중복제거시_순서유지(self):
        # given: 세 개의 URL 중 첫 번째와 세 번째가 동일
        docs = [
            {"content": "내용 A", "url": "https://spring.io/a"},
            {"content": "내용 B", "url": "https://spring.io/b"},
            {"content": "내용 C", "url": "https://spring.io/a"},  # 중복
        ]
        mock_gen = AsyncMock(return_value="답변")
        with (
            patch.object(rag_module, "search_docs", new=AsyncMock(return_value=docs)),
            patch.object(rag_module, "generate_answer", mock_gen),
        ):
            # when
            _, sources = run(rag_module.ask("질문"))

        # then: 첫 등장 순서대로 a → b
        self.assertEqual(["https://spring.io/a", "https://spring.io/b"], sources)

    # ------------------------------------------------------------------
    # Happy path
    # ------------------------------------------------------------------

    def test_정상케이스_answer와sources반환(self):
        # given
        docs = [
            {"content": "Spring Boot는 자동 설정을 제공합니다.", "url": "https://spring.io/boot"},
            {"content": "Spring MVC는 웹 레이어를 담당합니다.", "url": "https://spring.io/mvc"},
        ]
        mock_gen = AsyncMock(return_value="Spring Boot는 편리한 프레임워크입니다.")
        with (
            patch.object(rag_module, "search_docs", new=AsyncMock(return_value=docs)),
            patch.object(rag_module, "generate_answer", mock_gen),
        ):
            # when
            answer, sources = run(rag_module.ask("Spring Boot란?"))

        # then
        self.assertEqual("Spring Boot는 편리한 프레임워크입니다.", answer)
        self.assertEqual(["https://spring.io/boot", "https://spring.io/mvc"], sources)
        mock_gen.assert_called_once_with(
            "Spring Boot란?",
            ["Spring Boot는 자동 설정을 제공합니다.", "Spring MVC는 웹 레이어를 담당합니다."],
        )

    def test_url키없는doc_sources에_포함안됨(self):
        # given: url 키가 없는 doc
        docs = [
            {"content": "내용 A"},  # url 없음
            {"content": "내용 B", "url": "https://spring.io/b"},
        ]
        mock_gen = AsyncMock(return_value="답변")
        with (
            patch.object(rag_module, "search_docs", new=AsyncMock(return_value=docs)),
            patch.object(rag_module, "generate_answer", mock_gen),
        ):
            # when
            _, sources = run(rag_module.ask("질문"))

        # then: url 없는 doc은 sources에 포함되지 않음
        self.assertEqual(["https://spring.io/b"], sources)


if __name__ == "__main__":
    unittest.main()
