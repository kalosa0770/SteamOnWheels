import io
import os
import sqlite3
import threading
from contextlib import contextmanager
from datetime import datetime, timezone

import numpy as np
import torch
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from scipy.io.wavfile import write as write_wav
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer, VitsModel, AutoTokenizer as AutoTokenizerTTS

# Limit CPU threads to optimize memory and prevent OOM on host platforms
torch.set_num_threads(2)

app = FastAPI(title="Steam on Wheels Bemba Translation API")

MODEL_ID = "facebook/nllb-200-distilled-600M"
SRC_LANG = "eng_Latn"
TGT_LANG = "bem_Latn"

# Meta MMS text-to-speech models
TTS_MODEL_IDS = {
    "bem": "facebook/mms-tts-bem",
    "eng": "facebook/mms-tts-eng",
}

DB_PATH = os.path.join(os.path.dirname(__file__), "lessons.db")

tokenizer = None
model = None

# Cache of loaded TTS models and lock for thread safety
_tts_cache = {}
_tts_lock = threading.Lock()

STATIC_DIR = os.path.join(os.path.dirname(__file__), "static")
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")


# ---------------------------------------------------------------------------
# Database
# ---------------------------------------------------------------------------

@contextmanager
def get_db():
    # check_same_thread=False allows SQLite connections across FastAPI background threads
    conn = sqlite3.connect(DB_PATH, check_same_thread=False)
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


# ---------------------------------------------------------------------------
# Text-to-speech (Meta MMS / VITS)
# ---------------------------------------------------------------------------

def get_tts_model(lang: str):
    if lang not in TTS_MODEL_IDS:
        raise HTTPException(status_code=400, detail=f"Unsupported TTS language: {lang}")

    with _tts_lock:
        if lang not in _tts_cache:
            model_id = TTS_MODEL_IDS[lang]
            print(f"Loading MMS-TTS model for '{lang}' ({model_id})...")
            tts_tokenizer = AutoTokenizerTTS.from_pretrained(model_id)
            tts_model = VitsModel.from_pretrained(model_id)
            tts_model.eval()
            _tts_cache[lang] = (tts_tokenizer, tts_model)
            print(f"MMS-TTS model for '{lang}' loaded.")

    return _tts_cache[lang]


def synthesize_speech(text: str, lang: str) -> bytes:
    tts_tokenizer, tts_model = get_tts_model(lang)
    inputs = tts_tokenizer(text, return_tensors="pt")

    with torch.inference_mode():
        output = tts_model(**inputs).waveform

    waveform = output.squeeze().cpu().numpy()
    waveform = np.clip(waveform, -1.0, 1.0)
    pcm16 = (waveform * 32767).astype(np.int16)

    buffer = io.BytesIO()
    write_wav(buffer, rate=tts_model.config.sampling_rate, data=pcm16)
    buffer.seek(0)
    return buffer.read()


# ---------------------------------------------------------------------------
# Request Schemas
# ---------------------------------------------------------------------------

class TranslationRequest(BaseModel):
    inputs: str
    src_lang: str = SRC_LANG
    tgt_lang: str = TGT_LANG


class TTSRequest(BaseModel):
    text: str
    lang: str = "bem"  # "bem" or "eng"


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


@app.get("/lessons")
def lessons_page():
    return FileResponse(os.path.join(STATIC_DIR, "lessons.html"))


@app.get("/progress")
def progress_page():
    return FileResponse(os.path.join(STATIC_DIR, "progress.html"))


@app.get("/profile")
def profile_page():
    return FileResponse(os.path.join(STATIC_DIR, "profile.html"))


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


@app.post("/api/tts")
def text_to_speech(req: TTSRequest):
    if not req.text or not req.text.strip():
        raise HTTPException(status_code=400, detail="Text cannot be empty")

    try:
        audio_bytes = synthesize_speech(req.text.strip(), req.lang)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"TTS generation failed: {e}")

    return StreamingResponse(io.BytesIO(audio_bytes), media_type="audio/wav")


@app.get("/api/lessons/{subject}")
def get_lesson(subject: str):
    with get_db() as conn:
        row = conn.execute(
            "SELECT * FROM lessons WHERE subject = ? ORDER BY id DESC LIMIT 1",
            (subject,),
        ).fetchone()

    if row is None:
        return {
            "subject": subject,
            "topic_en": f"{subject} — No lessons uploaded yet",
            "topic_bem": f"{subject} — Tapali amasambililo",
            "content_en": "No lesson content has been uploaded for this subject yet.",
            "content_bem": "Tapali amasambililo ayabikwapo pali ino misango.",
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