import base64
import hashlib
import hmac
import io
import json
import os
import re
import secrets
import sqlite3
import threading
import time
from contextlib import contextmanager
from datetime import datetime, timezone

import numpy as np
import torch
from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from scipy.io.wavfile import write as write_wav
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer, VitsModel, AutoTokenizer as AutoTokenizerTTS

# Limit CPU threads to optimize memory and prevent OOM on host platforms
torch.set_num_threads(2)

app = FastAPI(title="Steam on Wheels Bemba Translation API")

# Using the "lite" distilled 600M checkpoint rather than the full 1.3B.
# This app targets grade 1-5 pupils, so lesson content is short, simple
# sentences - the gap in translation quality between 600M and 1.3B mostly
# shows up on long/complex sentences, which isn't what this audience needs.
# The 600M model is also ~2x lighter on RAM and meaningfully faster per
# request, which matters more here than squeezing out marginal quality on
# vocabulary these lessons won't use anyway. Most of the earlier translation
# problems (repetition loops, mid-sentence truncation) were decoding bugs,
# not a model-size issue - those fixes (beam search, no_repeat_ngram_size,
# sentence-level splitting) apply here too and are what actually matters.
MODEL_ID = "facebook/nllb-200-distilled-600M"
SRC_LANG = "eng_Latn"
TGT_LANG = "bem_Latn"

# Meta MMS text-to-speech models.
# NOTE: facebook/mms-tts-bem is currently the only openly available Bemba
# TTS checkpoint - there isn't a second/alternate Bemba voice to swap in.
TTS_MODEL_IDS = {
    "bem": "facebook/mms-tts-bem",
    "eng": "facebook/mms-tts-eng",
}

DB_PATH = os.path.join(os.path.dirname(__file__), "lessons.db")

# The fixed set of subjects pupils see on Home / Lessons / Progress.
SUBJECTS = ["Maths", "Literacy", "Science", "CTS"]

# IMPORTANT: set a real SECRET_KEY env var on Railway. This fallback is only
# for local/dev use - if it's left as-is in production, anyone can forge
# login tokens.
SECRET_KEY = os.environ.get("SECRET_KEY", "dev-insecure-secret-change-me")
TOKEN_TTL_SECONDS = 60 * 60 * 24 * 30  # 30 days

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
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('pupil', 'teacher')),
                created_at TEXT NOT NULL
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS progress (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                subject TEXT NOT NULL,
                viewed INTEGER NOT NULL DEFAULT 0,
                listened INTEGER NOT NULL DEFAULT 0,
                updated_at TEXT NOT NULL,
                UNIQUE(user_id, subject)
            )
            """
        )

        # Migration: add teacher_id to lessons if upgrading from an older DB
        # that was created before accounts existed.
        existing_cols = [row["name"] for row in conn.execute("PRAGMA table_info(lessons)").fetchall()]
        if "teacher_id" not in existing_cols:
            conn.execute("ALTER TABLE lessons ADD COLUMN teacher_id INTEGER")


init_db()


# ---------------------------------------------------------------------------
# Auth: password hashing + signed tokens
# ---------------------------------------------------------------------------
# Deliberately dependency-free (stdlib hashlib/hmac only) so nothing new
# needs to be added to requirements.txt / installed on Railway.

def _b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def _b64url_decode(data: str) -> bytes:
    padding = "=" * (-len(data) % 4)
    return base64.urlsafe_b64decode(data + padding)


def hash_password(password: str) -> str:
    salt = secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), 200_000)
    return f"{salt}${digest.hex()}"


def verify_password(password: str, password_hash: str) -> bool:
    try:
        salt, digest_hex = password_hash.split("$")
        expected = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), 200_000)
        return hmac.compare_digest(expected.hex(), digest_hex)
    except Exception:
        return False


def create_token(user_id: int, role: str) -> str:
    payload = {"user_id": user_id, "role": role, "exp": int(time.time()) + TOKEN_TTL_SECONDS}
    payload_b64 = _b64url_encode(json.dumps(payload).encode())
    sig = hmac.new(SECRET_KEY.encode(), payload_b64.encode(), hashlib.sha256).digest()
    return f"{payload_b64}.{_b64url_encode(sig)}"


def decode_token(token: str) -> dict:
    try:
        payload_b64, sig_b64 = token.split(".")
        expected_sig = hmac.new(SECRET_KEY.encode(), payload_b64.encode(), hashlib.sha256).digest()
        if not hmac.compare_digest(_b64url_encode(expected_sig), sig_b64):
            raise ValueError("bad signature")
        payload = json.loads(_b64url_decode(payload_b64))
        if payload["exp"] < time.time():
            raise ValueError("expired")
        return payload
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired token")


def get_current_user(authorization: str = Header(None)) -> sqlite3.Row:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Not authenticated")
    token = authorization[len("Bearer "):]
    payload = decode_token(token)
    with get_db() as conn:
        row = conn.execute("SELECT * FROM users WHERE id = ?", (payload["user_id"],)).fetchone()
    if row is None:
        raise HTTPException(status_code=401, detail="User not found")
    return row


def require_teacher(user: sqlite3.Row = Depends(get_current_user)) -> sqlite3.Row:
    if user["role"] != "teacher":
        raise HTTPException(status_code=403, detail="Teacher account required")
    return user


def user_to_dict(row: sqlite3.Row) -> dict:
    return {"id": row["id"], "name": row["name"], "email": row["email"], "role": row["role"]}


# ---------------------------------------------------------------------------
# Translation model
# ---------------------------------------------------------------------------

def get_model():
    global tokenizer, model
    if model is None or tokenizer is None:
        print(f"Loading NLLB tokenizer and model weights ({MODEL_ID}) on demand...")
        tokenizer = AutoTokenizer.from_pretrained(MODEL_ID, src_lang=SRC_LANG, tgt_lang=TGT_LANG)
        model = AutoModelForSeq2SeqLM.from_pretrained(MODEL_ID, low_cpu_mem_usage=True)
        model.eval()
        print("Model loaded successfully!")
    return tokenizer, model


# Splits on sentence-ending punctuation while keeping newlines as hard
# breaks, so bullet lists don't get merged into one run-on sentence.
_SENTENCE_SPLIT_RE = re.compile(r"(?<=[.!?])\s+")


def _split_into_segments(line: str):
    line = line.strip()
    if not line:
        return []
    return [seg.strip() for seg in _SENTENCE_SPLIT_RE.split(line) if seg.strip()]


def _translate_segment(tok, mdl, segment: str, tgt_lang: str) -> str:
    inputs = tok(segment, return_tensors="pt", truncation=True, max_length=256)
    forced_bos_token_id = tok.convert_tokens_to_ids(tgt_lang)

    input_len = inputs["input_ids"].shape[1]
    max_new = min(256, max(32, int(input_len * 2)))

    with torch.inference_mode():
        generated_tokens = mdl.generate(
            **inputs,
            forced_bos_token_id=forced_bos_token_id,
            max_new_tokens=max_new,
            num_beams=5,               # beam search instead of greedy - fixes repetition loops
            no_repeat_ngram_size=3,    # forbid repeating 3-grams
            repetition_penalty=1.3,    # penalize repeated tokens
            early_stopping=True,
        )
    return tok.batch_decode(generated_tokens, skip_special_tokens=True)[0]


def run_translation(text: str, tgt_lang: str = TGT_LANG) -> str:
    """Translates `text` line-by-line and sentence-by-sentence, which avoids
    the repetition-loop / truncation issues that came from feeding NLLB an
    entire multi-paragraph lesson as one blob, and preserves bullet-point
    formatting in the translated output."""
    tok, mdl = get_model()

    translated_lines = []
    for line in text.split("\n"):
        if not line.strip():
            translated_lines.append("")
            continue

        segments = _split_into_segments(line)
        translated_segments = [_translate_segment(tok, mdl, seg, tgt_lang) for seg in segments]
        translated_lines.append(" ".join(translated_segments))

    return "\n".join(translated_lines)


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


class SignupRequest(BaseModel):
    name: str
    email: str
    password: str
    role: str  # "pupil" or "teacher"


class LoginRequest(BaseModel):
    email: str
    password: str


class UpdateProfileRequest(BaseModel):
    name: str


class ChangePasswordRequest(BaseModel):
    current_password: str
    new_password: str


class ProgressUpdateRequest(BaseModel):
    subject: str
    event: str  # "viewed" or "listened"


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


@app.get("/account-settings")
def account_settings_page():
    return FileResponse(os.path.join(STATIC_DIR, "account-settings.html"))


@app.get("/login")
def login_page():
    return FileResponse(os.path.join(STATIC_DIR, "login.html"))


@app.get("/signup")
def signup_page():
    return FileResponse(os.path.join(STATIC_DIR, "signup.html"))


@app.get("/teacher/dashboard")
def teacher_dashboard_page():
    return FileResponse(os.path.join(STATIC_DIR, "teacher-dashboard.html"))


# ---------------------------------------------------------------------------
# Auth API routes
# ---------------------------------------------------------------------------

@app.post("/api/auth/signup")
def signup(req: SignupRequest):
    name = req.name.strip()
    email = req.email.strip().lower()
    role = req.role.strip().lower()

    if not name or not email or not req.password:
        raise HTTPException(status_code=400, detail="All fields are required")
    if role not in ("pupil", "teacher"):
        raise HTTPException(status_code=400, detail="Role must be 'pupil' or 'teacher'")
    if len(req.password) < 6:
        raise HTTPException(status_code=400, detail="Password must be at least 6 characters")

    password_hash = hash_password(req.password)
    created_at = datetime.now(timezone.utc).isoformat()

    with get_db() as conn:
        existing = conn.execute("SELECT id FROM users WHERE email = ?", (email,)).fetchone()
        if existing:
            raise HTTPException(status_code=409, detail="An account with this email already exists")
        cursor = conn.execute(
            "INSERT INTO users (name, email, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)",
            (name, email, password_hash, role, created_at),
        )
        user_id = cursor.lastrowid

    token = create_token(user_id, role)
    return {"token": token, "user": {"id": user_id, "name": name, "email": email, "role": role}}


@app.post("/api/auth/login")
def login(req: LoginRequest):
    email = req.email.strip().lower()
    with get_db() as conn:
        row = conn.execute("SELECT * FROM users WHERE email = ?", (email,)).fetchone()

    if row is None or not verify_password(req.password, row["password_hash"]):
        raise HTTPException(status_code=401, detail="Invalid email or password")

    token = create_token(row["id"], row["role"])
    return {"token": token, "user": user_to_dict(row)}


@app.get("/api/auth/me")
def me(user: sqlite3.Row = Depends(get_current_user)):
    return user_to_dict(user)


@app.put("/api/auth/me")
def update_me(req: UpdateProfileRequest, user: sqlite3.Row = Depends(get_current_user)):
    name = req.name.strip()
    if not name:
        raise HTTPException(status_code=400, detail="Name cannot be empty")

    with get_db() as conn:
        conn.execute("UPDATE users SET name = ? WHERE id = ?", (name, user["id"]))
        row = conn.execute("SELECT * FROM users WHERE id = ?", (user["id"],)).fetchone()

    return user_to_dict(row)


@app.post("/api/auth/change-password")
def change_password(req: ChangePasswordRequest, user: sqlite3.Row = Depends(get_current_user)):
    if not verify_password(req.current_password, user["password_hash"]):
        raise HTTPException(status_code=401, detail="Current password is incorrect")
    if len(req.new_password) < 6:
        raise HTTPException(status_code=400, detail="New password must be at least 6 characters")

    new_hash = hash_password(req.new_password)
    with get_db() as conn:
        conn.execute("UPDATE users SET password_hash = ? WHERE id = ?", (new_hash, user["id"]))

    return {"ok": True}


# ---------------------------------------------------------------------------
# Progress tracking (pupils only)
# ---------------------------------------------------------------------------
# Kept deliberately simple for a grade 1-5 audience: each subject is either
# not started (0%), opened (50%), or opened AND had its audio played (100%).
# There's no per-lesson granularity since each subject only ever has one
# "current" lesson (the latest one a teacher posted).

@app.post("/api/progress")
def update_progress(req: ProgressUpdateRequest, user: sqlite3.Row = Depends(get_current_user)):
    if user["role"] != "pupil":
        # Teachers can open /lesson to preview content; that shouldn't
        # create or affect any pupil's progress record.
        return {"ok": True, "skipped": True}
    if req.event not in ("viewed", "listened"):
        raise HTTPException(status_code=400, detail="event must be 'viewed' or 'listened'")

    updated_at = datetime.now(timezone.utc).isoformat()
    with get_db() as conn:
        existing = conn.execute(
            "SELECT * FROM progress WHERE user_id = ? AND subject = ?",
            (user["id"], req.subject),
        ).fetchone()

        if existing is None:
            conn.execute(
                "INSERT INTO progress (user_id, subject, viewed, listened, updated_at) VALUES (?, ?, ?, ?, ?)",
                (user["id"], req.subject, 1, 1 if req.event == "listened" else 0, updated_at),
            )
        else:
            listened = existing["listened"] or (1 if req.event == "listened" else 0)
            conn.execute(
                "UPDATE progress SET viewed = 1, listened = ?, updated_at = ? WHERE id = ?",
                (listened, updated_at, existing["id"]),
            )

    return {"ok": True}


@app.get("/api/progress")
def get_progress(user: sqlite3.Row = Depends(get_current_user)):
    with get_db() as conn:
        rows = conn.execute(
            "SELECT subject, viewed, listened FROM progress WHERE user_id = ?",
            (user["id"],),
        ).fetchall()
    by_subject = {row["subject"]: row for row in rows}

    result = []
    for subject in SUBJECTS:
        row = by_subject.get(subject)
        if row is None:
            pct = 0
        elif row["listened"]:
            pct = 100
        elif row["viewed"]:
            pct = 50
        else:
            pct = 0
        result.append({"subject": subject, "pct": pct})
    return result


# ---------------------------------------------------------------------------
# API routes
# ---------------------------------------------------------------------------

@app.get("/api/health")
def health():
    return {"status": "online", "app": "steamonwheels", "translation_model": MODEL_ID}


@app.post("/translate")
def translate(req: TranslationRequest):
    if not req.inputs or not req.inputs.strip():
        raise HTTPException(status_code=400, detail="Input text cannot be empty")
    result = run_translation(req.inputs, req.tgt_lang)
    return [{"translation_text": result}]


@app.post("/api/tts")
def text_to_speech(req: TTSRequest, user: sqlite3.Row = Depends(get_current_user)):
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
def get_lesson(subject: str, user: sqlite3.Row = Depends(get_current_user)):
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
def create_lesson(req: LessonCreateRequest, teacher: sqlite3.Row = Depends(require_teacher)):
    if not req.subject.strip() or not req.topic_en.strip() or not req.content_en.strip():
        raise HTTPException(status_code=400, detail="All fields are required")

    topic_bem = run_translation(req.topic_en)
    content_bem = run_translation(req.content_en)

    created_at = datetime.now(timezone.utc).isoformat()

    with get_db() as conn:
        cursor = conn.execute(
            """
            INSERT INTO lessons (subject, topic_en, topic_bem, content_en, content_bem, created_at, teacher_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                req.subject.strip(),
                req.topic_en.strip(),
                topic_bem,
                req.content_en.strip(),
                content_bem,
                created_at,
                teacher["id"],
            ),
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


@app.get("/api/teacher/lessons")
def teacher_lessons(teacher: sqlite3.Row = Depends(require_teacher)):
    """Most recent lesson per subject posted by this teacher, for the dashboard."""
    with get_db() as conn:
        rows = conn.execute(
            """
            SELECT * FROM lessons
            WHERE teacher_id = ?
            AND id IN (SELECT MAX(id) FROM lessons WHERE teacher_id = ? GROUP BY subject)
            ORDER BY id DESC
            """,
            (teacher["id"], teacher["id"]),
        ).fetchall()

    return [
        {
            "id": row["id"],
            "subject": row["subject"],
            "topic_en": row["topic_en"],
            "topic_bem": row["topic_bem"],
            "created_at": row["created_at"],
        }
        for row in rows
    ]