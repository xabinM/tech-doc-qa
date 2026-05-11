# RAG Server Overview
Python FastAPI 기반 RAG(Retrieval-Augmented Generation) 서버.
Elasticsearch에서 관련 문서 청크를 검색하고, Claude API로 답변을 생성한다.
백엔드(Spring Boot)의 요청을 받아 처리하며, 외부에 직접 노출되지 않는 내부 서비스다.

# Current Status
- [x] POST /ask 엔드포인트 (질문 → 검색 → 답변 + 출처 반환)
- [x] GET /health 엔드포인트 (ES 연결 상태 확인)
- [x] BM25 + kNN 하이브리드 검색 (sentence-transformers)
- [x] Claude API prompt caching 적용
- [x] ES/Anthropic/일반 예외 핸들러
- [x] 문서 색인 스크립트 (Spring Boot 8개 + Spring Framework/Data JPA/Security 7개)

# Environment
- Python: 3.11+
- Framework: FastAPI 0.115
- Search: Elasticsearch 8.13 (async)
- LLM: Anthropic Claude API (claude-sonnet-4-6)
- Embedding: sentence-transformers (all-MiniLM-L6-v2, 384dims)

# Architecture
계층 구조: router → service → client

```
router/      ← HTTP 엔드포인트, 요청/응답 스키마 (Pydantic)
service/     ← 비즈니스 로직 오케스트레이션
client/      ← 외부 시스템 연동 (ES, LLM)
scripts/     ← 운영 스크립트 (문서 색인)
```

# Package Structure
```
rag-server/
  main.py          ← FastAPI 앱, 예외 핸들러, health 엔드포인트
  config.py        ← 환경변수 기반 설정 (pydantic-settings)
  router/
    query.py       ← POST /ask
  service/
    rag.py         ← ES 검색 → LLM 답변 생성 오케스트레이션
  client/
    es.py          ← AsyncElasticsearch, 임베딩 모델, 하이브리드 검색
    llm.py         ← AsyncAnthropic, 답변 생성 (prompt caching 적용)
  scripts/
    index_docs.py  ← 문서 크롤링 → 임베딩 → ES 색인
```

# API Spec
```
POST /ask       { question: str } → { answer: str, sources: list[str] }
GET  /health    → { status: "ok" } | 503 { status: "unhealthy", detail: str }
```

# Key Design Decisions
- ES 검색: `multi_match`(BM25) + `knn` 동시 실행으로 하이브리드 점수 결합
- 임베딩 모델: 앱 시작 시 1회 로딩 (`_model = SentenceTransformer(...)`)
- prompt caching: system 프롬프트에 `cache_control: ephemeral` 적용
- sources: 동일 URL 중복 제거 후 순서 유지 (`dict.fromkeys`)
- 예외 처리: `TransportError`(ES), `APIError`(Anthropic), `Exception`(일반) 계층별 핸들러

# Rules
- 환경변수는 `config.py`의 `Settings` 클래스를 통해서만 접근, 직접 `os.getenv` 사용 금지
- ES 인덱스 재생성 시 `index-mapping.json` 기준으로 매핑 적용 (dense_vector dims: 384)
- 색인 스크립트 실행 전 기존 인덱스 삭제 필요 (매핑 변경 시)
- LLM 응답 접근 시 `content[0].type == "text"` 검증 후 `.text` 접근
