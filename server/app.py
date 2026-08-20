import os
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone

import torch
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

app = FastAPI(title="Steam on Wheels Bemba Translation API")

MODEL_ID = "Wana1708/nllb-bemba-education"
SRC_LANG = "eng_Latn"
TGT_LANG = "bem_Latn"

DB_PATH = os.path.join(os.path.dirname(__file__), "lessons.db")

tokenizer = None
model = None

STATIC_DIR = os.path.join(os.path.dirname(__file__), "static")
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")


# ---------------------------------------------------------------------------
# Database
# ---------------------------------------------------------------------------

@contextmanager
def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db():
    with get_db() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS lessons (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject TEXT NOT NULL,
                topic_en TEXT NOT NULL,
                topic_bem TEXT NOT NULL,
                content_en TEXT NOT NULL,
                content_bem TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """
        )


init_db()


# ---------------------------------------------------------------------------
# Translation model
# ---------------------------------------------------------------------------

def get_model():
    global tokenizer, model
    if model is None or tokenizer is None:
        print("Loading NLLB tokenizer and model weights on demand...")
        tokenizer = AutoTokenizer.from_pretrained(MODEL_ID, src_lang=SRC_LANG, tgt_lang=TGT_LANG)
        model = AutoModelForSeq2SeqLM.from_pretrained(MODEL_ID, low_cpu_mem_usage=True)
        model.eval()
        print("Model loaded successfully!")
    return tokenizer, model


def run_translation(text: str, tgt_lang: str = TGT_LANG) -> str:
    tok, mdl = get_model()
    inputs = tok(text, return_tensors="pt")
    forced_bos_token_id = tok.convert_tokens_to_ids(tgt_lang)
    with torch.inference_mode():
        generated_tokens = mdl.generate(
            **inputs,
            forced_bos_token_id=forced_bos_token_id,
            max_length=128,
        )
    return tok.batch_decode(generated_tokens, skip_special_tokens=True)[0]


class TranslationRequest(BaseModel):
    inputs: str
    src_lang: str = SRC_LANG
    tgt_lang: str = TGT_LANG


class LessonCreateRequest(BaseModel):
    subject: str
    topic_en: str
    content_en: str


# ---------------------------------------------------------------------------
# Frontend routes
# ---------------------------------------------------------------------------

@app.get("/")
def home_page():
    return FileResponse(os.path.join(STATIC_DIR, "index.html"))


@app.get("/lesson")
def lesson_page():
    return FileResponse(os.path.join(STATIC_DIR, "lesson.html"))


@app.get("/upload")
def upload_page():
    return FileResponse(os.path.join(STATIC_DIR, "upload.html"))


# ---------------------------------------------------------------------------
# API routes
# ---------------------------------------------------------------------------

@app.get("/api/health")
def health():
    return {"status": "online", "app": "steamonwheels"}


@app.post("/translate")
def translate(req: TranslationRequest):
    if not req.inputs or not req.inputs.strip():
        raise HTTPException(status_code=400, detail="Input text cannot be empty")
    result = run_translation(req.inputs, req.tgt_lang)
    return [{"translation_text": result}]


@app.get("/api/lessons/{subject}")
def get_lesson(subject: str):
    with get_db() as conn:
        row = conn.execute(
            "SELECT * FROM lessons WHERE subject = ? ORDER BY id DESC LIMIT 1",
            (subject,),
        ).fetchone()

    if row is None:
        # Fallback content so the lesson screen always has something to show
        return {
            "subject": subject,
            "topic_en": f"{subject} — No lessons uploaded yet",
            "topic_bem": f"{subject} — Tapali amasambililo",
            "content_en": "No lesson content has been uploaded for this subject yet. Use the Upload screen to add one.",
            "content_bem": "Tapali amasambililo ayabikwapo pali ino misango. Bomfyeni Upload pa kubika limo.",
        }

    return {
        "subject": row["subject"],
        "topic_en": row["topic_en"],
        "topic_bem": row["topic_bem"],
        "content_en": row["content_en"],
        "content_bem": row["content_bem"],
    }


@app.post("/api/lessons")
def create_lesson(req: LessonCreateRequest):
    if not req.subject.strip() or not req.topic_en.strip() or not req.content_en.strip():
        raise HTTPException(status_code=400, detail="All fields are required")

    topic_bem = run_translation(req.topic_en)
    content_bem = run_translation(req.content_en)

    created_at = datetime.now(timezone.utc).isoformat()

    with get_db() as conn:
        cursor = conn.execute(
            """
            INSERT INTO lessons (subject, topic_en, topic_bem, content_en, content_bem, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (req.subject.strip(), req.topic_en.strip(), topic_bem, req.content_en.strip(), content_bem, created_at),
        )
        lesson_id = cursor.lastrowid

    return {
        "id": lesson_id,
        "subject": req.subject.strip(),
        "topic_en": req.topic_en.strip(),
        "topic_bem": topic_bem,
        "content_en": req.content_en.strip(),
        "content_bem": content_bem,
    }
