"""
POST /ask 엔드포인트 통합 테스트.
외부 의존성(ES, LLM)은 service.rag.ask를 mock 처리.
"""
import os
import sys
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

os.environ.setdefault("ES_URL", "http://localhost:9200")
os.environ.setdefault("CLAUDE_API_KEY", "test-key")

# elasticsearch, sentence_transformers, anthropic이 설치되지 않은 환경에서도
# 임포트가 성공하도록 sys.modules에 stub을 미리 주입한다.
#
# main.py의 @app.exception_handler()는 등록 시점에 issubclass() 검사를 하므로
# APIError / TransportError는 반드시 실제 Exception 서브클래스여야 한다.

class _FakeAnthropicAPIError(Exception):
    pass

class _FakeESTransportError(Exception):
    pass

# main.py의 @app.exception_handler()는 등록 시점에 issubclass() 검사를 하므로
# APIError / TransportError는 반드시 실제 Exception 서브클래스여야 한다.
# 다른 테스트 파일의 MagicMock stub이 먼저 등록돼도 반드시 올바른 클래스로
# 교체해야 하므로 setdefault 대신 직접 할당한다.
_anthropic_stub = MagicMock()
_anthropic_stub.AsyncAnthropic = MagicMock(return_value=MagicMock())
_anthropic_stub.APIError = _FakeAnthropicAPIError
sys.modules["anthropic"] = _anthropic_stub

_es_exceptions_stub = MagicMock()
_es_exceptions_stub.TransportError = _FakeESTransportError
_elasticsearch_stub = MagicMock()
_elasticsearch_stub.exceptions = _es_exceptions_stub
sys.modules["elasticsearch"] = _elasticsearch_stub
sys.modules["elasticsearch.exceptions"] = _es_exceptions_stub
sys.modules.setdefault("sentence_transformers", MagicMock())

_es_client_stub = MagicMock()
_es_client_stub.search_docs = AsyncMock(return_value=[])
_es_client_stub.ping = AsyncMock(return_value=True)
_es_client_stub.load_model = AsyncMock()
_es_client_stub.close_es = AsyncMock()
sys.modules["client.es"] = _es_client_stub

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi.testclient import TestClient


def _build_client():
    """
    main.py를 (재)임포트해 TestClient를 생성한다.
    모듈 캐시를 제거해 매번 깨끗한 앱 인스턴스를 얻는다.
    """
    for mod in ("main", "router.query", "service.rag", "client.llm"):
        sys.modules.pop(mod, None)

    # lifespan의 load_model / close_es를 noop으로 교체
    with (
        patch("client.es.load_model", new=AsyncMock()),
        patch("client.es.close_es", new=AsyncMock()),
    ):
        import main as _main
        return TestClient(_main.app, raise_server_exceptions=False)


class TestAskEndpoint(unittest.TestCase):

    def setUp(self):
        self.client = _build_client()

    # ------------------------------------------------------------------
    # Helper
    # ------------------------------------------------------------------

    def _post_ask(self, question, headers=None):
        return self.client.post("/ask", json={"question": question}, headers=headers or {})

    # ------------------------------------------------------------------
    # Happy path
    # ------------------------------------------------------------------

    def test_정상요청_200응답(self):
        # given
        # router.query는 "from service import rag"로 rag 모듈 자체를 참조하므로
        # router.query.rag.ask를 패치해야 실제 핸들러에서 교체된 함수가 호출된다.
        mock_answer = ("테스트 답변", ["https://example.com"])
        with patch("router.query.rag.ask", new=AsyncMock(return_value=mock_answer)):
            # when
            response = self._post_ask("Spring이란?")

        # then
        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("테스트 답변", body["answer"])
        self.assertEqual(["https://example.com"], body["sources"])

    # ------------------------------------------------------------------
    # Edge cases: 요청 유효성 검사
    # ------------------------------------------------------------------

    def test_question_빈문자열_422응답(self):
        # when
        response = self._post_ask("")

        # then
        self.assertEqual(422, response.status_code)

    def test_question_2001자초과_422응답(self):
        # given: 경계값 초과
        response = self._post_ask("가" * 2001)

        # then
        self.assertEqual(422, response.status_code)

    def test_question_정확히2000자_200응답(self):
        # given: 경계값 — 2000자는 허용
        mock_answer = ("답변", [])
        with patch("router.query.rag.ask", new=AsyncMock(return_value=mock_answer)):
            response = self._post_ask("가" * 2000)

        # then
        self.assertEqual(200, response.status_code)

    # ------------------------------------------------------------------
    # Edge cases: internal_secret 인증
    #
    # router/query.py의 _verify_secret()은 임포트 시점에 바인딩된
    # settings 객체를 참조하므로, "router.query.settings"를 직접 패치한다.
    # ------------------------------------------------------------------

    def test_internal_secret설정시_헤더없음_403응답(self):
        # given: secret이 설정된 상황 — 헤더 미전송
        mock_settings = MagicMock()
        mock_settings.internal_secret = "super-secret"
        with patch("router.query.settings", mock_settings):
            response = self._post_ask("질문")

        # then
        self.assertEqual(403, response.status_code)

    def test_internal_secret설정시_헤더불일치_403응답(self):
        # given
        mock_settings = MagicMock()
        mock_settings.internal_secret = "super-secret"
        with patch("router.query.settings", mock_settings):
            response = self._post_ask(
                "질문",
                headers={"X-Internal-Secret": "wrong-secret"},
            )

        # then
        self.assertEqual(403, response.status_code)

    def test_internal_secret설정시_헤더일치_200응답(self):
        # given
        mock_settings = MagicMock()
        mock_settings.internal_secret = "super-secret"
        mock_answer = ("답변", [])
        with (
            patch("router.query.settings", mock_settings),
            patch("router.query.rag.ask", new=AsyncMock(return_value=mock_answer)),
        ):
            response = self._post_ask(
                "질문",
                headers={"X-Internal-Secret": "super-secret"},
            )

        # then
        self.assertEqual(200, response.status_code)

    def test_internal_secret미설정시_헤더없어도_200응답(self):
        # given: internal_secret=None → 인증 스킵
        mock_settings = MagicMock()
        mock_settings.internal_secret = None
        mock_answer = ("답변", [])
        with (
            patch("router.query.settings", mock_settings),
            patch("router.query.rag.ask", new=AsyncMock(return_value=mock_answer)),
        ):
            response = self._post_ask("질문")

        # then
        self.assertEqual(200, response.status_code)


if __name__ == "__main__":
    unittest.main()
