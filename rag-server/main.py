from fastapi import FastAPI
from router.query import router

app = FastAPI(title="RAG Server")
app.include_router(router)
