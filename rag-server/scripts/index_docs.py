#!/usr/bin/env python3
"""Spring/Java 공식 문서를 Elasticsearch에 색인하는 스크립트.

실행 방법:
    ES_URL=http://localhost:9200 python scripts/index_docs.py
"""

import hashlib
import os
import sys
import time

import requests
from bs4 import BeautifulSoup
from elasticsearch import Elasticsearch, helpers
from sentence_transformers import SentenceTransformer

ES_URL = os.getenv("ES_URL", "http://localhost:9200")
INDEX = os.getenv("ES_INDEX", "tech-docs")
CHUNK_WORDS = 400

DOCS = [
    # Spring Boot
    ("Spring Boot - Web (Servlet)",        "https://docs.spring.io/spring-boot/reference/web/servlet.html"),
    ("Spring Boot - Security",             "https://docs.spring.io/spring-boot/reference/web/spring-security.html"),
    ("Spring Boot - Data (SQL)",           "https://docs.spring.io/spring-boot/reference/data/sql.html"),
    ("Spring Boot - Testing",              "https://docs.spring.io/spring-boot/reference/testing/index.html"),
    ("Spring Boot - Actuator",             "https://docs.spring.io/spring-boot/reference/actuator/index.html"),
    ("Spring Boot - Logging",              "https://docs.spring.io/spring-boot/reference/features/logging.html"),
    ("Spring Boot - Externalized Config",  "https://docs.spring.io/spring-boot/reference/features/external-config.html"),
    ("Spring Boot - Caching",              "https://docs.spring.io/spring-boot/reference/io/caching.html"),
    # Spring Framework
    ("Spring Framework - Core (IoC/DI)",   "https://docs.spring.io/spring-framework/reference/core/beans.html"),
    ("Spring Framework - AOP",             "https://docs.spring.io/spring-framework/reference/core/aop.html"),
    ("Spring Framework - Data Access",     "https://docs.spring.io/spring-framework/reference/data-access.html"),
    ("Spring Framework - Web MVC",         "https://docs.spring.io/spring-framework/reference/web/webmvc.html"),
    ("Spring Framework - Testing",         "https://docs.spring.io/spring-framework/reference/testing.html"),
    # Spring Data JPA
    ("Spring Data JPA",                    "https://docs.spring.io/spring-data/jpa/reference/jpa.html"),
    # Spring Security
    ("Spring Security - Servlet",          "https://docs.spring.io/spring-security/reference/servlet/index.html"),
]

MAPPING = {
    "mappings": {
        "properties": {
            "title":   {"type": "text"},
            "content": {"type": "text"},
            "url":     {"type": "keyword"},
        }
    }
}


def setup_index(es: Elasticsearch) -> None:
    if es.indices.exists(index=INDEX):
        print(f"인덱스 '{INDEX}' 이미 존재합니다.")
        return
    es.indices.create(index=INDEX, body=MAPPING)
    print(f"인덱스 '{INDEX}' 생성 완료")


def fetch_chunks(title: str, url: str) -> list[dict]:
    try:
        resp = requests.get(url, timeout=15, headers={"User-Agent": "Mozilla/5.0"})
        resp.raise_for_status()
    except requests.RequestException as e:
        print(f"  [오류] {url}: {e}")
        return []

    soup = BeautifulSoup(resp.text, "html.parser")
    for tag in soup.select("nav, header, footer, .navbar, .toc, script, style"):
        tag.decompose()

    words = soup.get_text(separator=" ", strip=True).split()

    chunks = []
    for i in range(0, len(words), CHUNK_WORDS):
        content = " ".join(words[i : i + CHUNK_WORDS])
        doc_id = hashlib.md5(f"{url}:{i}".encode()).hexdigest()
        chunks.append({"_id": doc_id, "title": title, "content": content, "url": url})
    return chunks


def index_chunks(es: Elasticsearch, chunks: list[dict], model: SentenceTransformer) -> None:
    texts = [chunk["content"] for chunk in chunks]
    embeddings = model.encode(texts, show_progress_bar=False, batch_size=32)

    actions = [
        {
            "_index": INDEX,
            "_id": chunk["_id"],
            "_source": {
                "title": chunk["title"],
                "content": chunk["content"],
                "url": chunk["url"],
                "embedding": embedding.tolist(),
            },
        }
        for chunk, embedding in zip(chunks, embeddings)
    ]
    success, errors = helpers.bulk(es, actions, raise_on_error=False)
    if errors:
        print(f"  [경고] {len(errors)}개 문서 색인 실패")
    print(f"  {success}개 청크 색인 완료")


def main() -> None:
    es = Elasticsearch(ES_URL)

    if not es.ping():
        print("Elasticsearch에 연결할 수 없습니다. ES가 실행 중인지 확인하세요.")
        sys.exit(1)

    setup_index(es)

    print("임베딩 모델 로딩 중...")
    model = SentenceTransformer("all-MiniLM-L6-v2")
    print("모델 로딩 완료\n")

    for title, url in DOCS:
        print(f"크롤링: {title}")
        chunks = fetch_chunks(title, url)
        if chunks:
            index_chunks(es, chunks, model)
        time.sleep(0.5)

    print("\n색인 완료!")


if __name__ == "__main__":
    main()
