# Steam on Wheels

A learning app for a mobile classroom program in Zambia, serving grade 1–5
pupils. Teachers upload lesson content in English; the backend translates it
to Bemba automatically. Pupils browse lessons by subject, read them in either
language, and listen to them read aloud via text-to-speech.

The project has three parts that share one backend:

| Layer | What it is | Docs |
|---|---|---|
| **Backend** | FastAPI server: auth, lessons, translation, TTS, progress tracking | [docs/BACKEND.md](docs/BACKEND.md) |
| **Web app** | Plain HTML/JS/Tailwind pages served by the backend itself | [docs/WEB.md](docs/WEB.md) |
| **Android app** | Native Java app hitting the same backend API | [docs/ANDROID.md](docs/ANDROID.md) |

Deployment specifics (Railway, resource limits, model choices) are in
[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## How it fits together

```
                     ┌─────────────────────────────┐
                     │      Railway (app.py)        │
                     │  FastAPI + SQLite + NLLB +    │
                     │        Meta MMS-TTS           │
                     └───────────────┬───────────────┘
                                      │  same REST API
                  ┌───────────────────┴───────────────────┐
                  │                                        │
        ┌─────────▼─────────┐                  ┌───────────▼──────────┐
        │   Web app          │                  │   Android app         │
        │   (static/*.html,  │                  │   (com.steamonwheels) │
        │   served by app.py)│                  │   OkHttp + Room       │
        └────────────────────┘                  └────────────────────────┘
```

Both clients talk to the exact same endpoints, share the same auth token
format, and see the same database — a pupil can sign up on the web and log
in on Android with the same account, and vice versa.

## Who uses what

- **Pupils** sign up, browse subjects (Maths, Literacy, Science, CTS), open
  lessons, read them in English or Bemba, listen to either version read
  aloud, and see their own progress per subject.
- **Teachers** sign up separately, land on a dashboard listing everything
  they've posted, and upload new lessons — writing English content, running
  it through the translator, reviewing/editing the Bemba result, then saving.

A single subject can have many lessons under it ("sections") — uploading
never overwrites an earlier lesson, it adds a new one.

## Quick start

**Backend** — see [docs/BACKEND.md](docs/BACKEND.md) for the full endpoint
reference and setup.

**Web app** — nothing to build; it's served directly by the backend at
`/`, `/login`, `/lesson`, etc. Editing `static/input.css` requires
rebuilding `static/output.css` via Tailwind before it takes effect (see
[docs/WEB.md](docs/WEB.md)).

**Android app** — see [docs/ANDROID.md](docs/ANDROID.md) for the file
structure and a from-scratch build walkthrough (this project was built and
tested without Android Studio, using the command-line SDK tools and
`gradlew` directly).

## Known limitations, honestly

- **Progress tracking is per-subject, not per-lesson.** Opening any lesson
  under Science marks "Science" as viewed — there's no per-lesson
  completion tracking. Would need a DB shape change (progress rows keyed by
  lesson id, not subject) to go further.
- **Translation quality is capped by RAM.** The backend runs on Railway's
  Hobby plan (hard 8GB RAM ceiling per service — confirmed in Railway's own
  dashboard, not just their marketing copy). That currently limits the NLLB
  model to `nllb-200-distilled-1.3B`. The full `nllb-200-3.3B` model would
  give better Bemba but needs ~10–13GB alone, which doesn't fit — see
  [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for the full tradeoff.
- **No bulk re-translation tool.** Lessons already saved keep whatever
  Bemba text was generated at the time — upgrading the model later doesn't
  retroactively improve old lessons.
- **The Android app icon and drawables are functional placeholders**, not
  final design assets — built to unblock a working build, not as finished
  visual design.
