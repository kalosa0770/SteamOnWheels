# Backend — `app.py`

Single-file FastAPI application. Handles auth, lessons, translation,
text-to-speech, and progress tracking, and also serves the web app's static
files.

## Setup

```
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000
```

The SQLite database (`lessons.db`) and its tables are created automatically
on startup — no manual migration step for a fresh install. On an existing
database, `init_db()` also runs a couple of lightweight migrations (adding
`teacher_id` to `lessons` if missing) so upgrading in place is safe.

### Required environment variable

```
SECRET_KEY=<any long random string>
```

Used to sign auth tokens. There's a dev fallback hardcoded in `app.py` —
**it is not safe for production.** Set a real one before deploying anywhere
real.

## Auth

Tokens are a lightweight custom scheme, not a third-party JWT library —
deliberately dependency-free (stdlib `hashlib`/`hmac` only), so nothing new
needed adding to `requirements.txt`.

- Passwords: `hashlib.pbkdf2_hmac` with a random salt, 200,000 iterations.
- Tokens: `base64url(payload).base64url(hmac_sha256(payload))`, where
  payload is `{user_id, role, exp}`. 30-day expiry.
- Every protected endpoint takes the token via `Authorization: Bearer <token>`
  and resolves it through the `get_current_user` dependency. Endpoints that
  are teacher-only use `require_teacher` instead, which layers a role check
  on top.

Two roles: `pupil` and `teacher`, chosen at signup and fixed after that —
there's no admin/upgrade path from pupil to teacher.

## Database schema

Three tables, all in `lessons.db`:

**`users`**
| column | type | notes |
|---|---|---|
| id | INTEGER PK | |
| name | TEXT | |
| email | TEXT UNIQUE | used for login |
| password_hash | TEXT | `salt$digest_hex` |
| role | TEXT | `pupil` or `teacher` |
| created_at | TEXT | ISO 8601 |

**`lessons`**
| column | type | notes |
|---|---|---|
| id | INTEGER PK | |
| subject | TEXT | e.g. `Science` — not a foreign key, just a string the pupil UI happens to offer 4 fixed values for (`Maths`, `Literacy`, `Science`, `CTS`) |
| topic_en / topic_bem | TEXT | |
| content_en / content_bem | TEXT | |
| created_at | TEXT | ISO 8601 |
| teacher_id | INTEGER | who posted it — nullable (migration column) |

A subject can have many rows — each upload is a new "section," never an
overwrite of a previous one.

**`progress`**
| column | type | notes |
|---|---|---|
| id | INTEGER PK | |
| user_id | INTEGER | |
| subject | TEXT | |
| viewed / listened | INTEGER (0/1) | |
| updated_at | TEXT | ISO 8601 |

One row per `(user_id, subject)` pair — `UNIQUE(user_id, subject)`. Progress
is tracked at the subject level, not per lesson (see the top-level README's
Known Limitations).

## Endpoint reference

### Pages (serve static HTML, no auth check at the route level — the pages
themselves call `requireAuth()` client-side via `auth.js`)

`GET /`, `/login`, `/signup`, `/lesson`, `/subject`, `/lessons`,
`/progress`, `/profile`, `/account-settings`, `/upload`,
`/teacher/dashboard`

### Auth

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/signup` | none | body: `{name, email, password, role}`. Returns `{token, user}`. |
| POST | `/api/auth/login` | none | body: `{email, password}`. Returns `{token, user}`. |
| GET | `/api/auth/me` | any | Returns the current user. |
| PUT | `/api/auth/me` | any | body: `{name}`. Updates display name only — email isn't editable. |
| POST | `/api/auth/change-password` | any | body: `{current_password, new_password}`. Verifies the current password server-side before allowing the change. |

### Lessons

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/subjects/{subject}/lessons` | any | Every lesson under that subject, newest first. |
| GET | `/api/lessons/{lesson_id}` | any | One lesson's full content. |
| POST | `/api/lessons` | teacher | body: `{subject, topic_en, content_en, topic_bem, content_bem}`. **Saves exactly what's given — does not translate internally.** Bemba fields must be pre-filled via `POST /translate` (and optionally edited) client-side first. |
| GET | `/api/teacher/lessons` | teacher | Every lesson *that teacher* has posted, across all subjects, sorted by subject then newest. |

### Translation & speech

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/translate` | teacher | body: `{inputs, src_lang, tgt_lang}`. Runs NLLB on one piece of text, returns `[{"translation_text": "..."}]`. Doesn't save anything — this is the "preview" step in the upload flow. |
| POST | `/api/tts` | any | body: `{text, lang}` (`lang` is `"bem"` or `"eng"`). Returns raw WAV audio bytes. |

### Progress

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/progress` | any | body: `{subject, event}` (`event` is `"viewed"` or `"listened"`). Teachers hitting this get silently no-op'd server-side — previewing a lesson as a teacher doesn't affect anyone's progress. |
| GET | `/api/progress` | any | Returns `[{subject, pct}]` for all 4 subjects — `pct` is 0 (never opened), 50 (opened), or 100 (opened + audio played). |

### Misc

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/health` | none | `{status, app, translation_model}` |

## Translation pipeline

Model: `facebook/nllb-200-distilled-1.3B` (see
[DEPLOYMENT.md](DEPLOYMENT.md) for why this size and not bigger/smaller).

Loaded lazily on first use, not at server startup — the first translation
request after a deploy or restart will be slow while the model downloads
and loads into memory; subsequent requests are fast.

`run_translation()` splits input on newlines, then on sentence boundaries,
translating each piece independently with beam search
(`num_beams=5`, `no_repeat_ngram_size=3`, `repetition_penalty=1.3`). This
splitting was a real fix, not a style choice — feeding NLLB a whole
multi-paragraph lesson as one blob caused repetition loops and mid-sentence
truncation; per-sentence translation with beam search fixed both.

**Teacher review step**: `POST /translate` produces a first-pass Bemba
translation, but `POST /api/lessons` doesn't call the model at all — it
just saves whatever's in the request body. The web and Android upload
flows both call `/translate` first, show the result in editable fields,
and only call `/api/lessons` once the teacher confirms (with or without
edits). This exists because even a good NLLB checkpoint produces
occasionally awkward Bemba — the model gets you most of the way, a human
gets you the rest of the way.

## Text-to-speech pipeline

Two models, also lazily loaded and cached per-language after first use:

```python
TTS_MODEL_IDS = {
    "bem": "facebook/mms-tts-bem",
    "eng": "facebook/mms-tts-eng",
}
```

`facebook/mms-tts-bem` is, as far as could be found, the only openly
available Bemba TTS checkpoint — there's no alternate/more-fluent Bemba
voice to swap in if the current one sounds robotic. The main lever for
naturalness is feeding it clean, sentence-segmented text (which the
translation pipeline already produces) rather than large unpunctuated
blocks.
