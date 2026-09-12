# Deployment

## Backend — Railway

Deployed as a standard Python service (`uvicorn app:app`). No Dockerfile
needed unless you want one — Railway can build directly from
`requirements.txt`.

### Required environment variable

```
SECRET_KEY=<long random string>
```

Without this set, `app.py` falls back to a hardcoded dev value — fine for
local testing, **not safe in production** (anyone who read this codebase
could forge login tokens against a deployment still using the fallback).

### Resource constraints — read this before changing `MODEL_ID`

Railway's **Hobby plan hard-caps each service at 8GB RAM / 8vCPU** — this
is confirmed directly in Railway's own dashboard (Settings → Resources →
"Plan limit: 8 GB"), not just a number from their marketing page. Deleting
other services in the same account does **not** raise this — it's a
per-service ceiling on the plan tier itself, not a shared pool. Confirmed
via Railway's own community forum, including replies from a Railway team
member: *"every single service and replica gets access to the full
resources allowed, there is nothing shared."*

This ceiling includes everything running in the container at once: the
NLLB translation model, both TTS models (loaded lazily and kept cached
after first use), the FastAPI process itself, and per-request memory
spikes during beam search.

**Model size vs. RAM, in practice:**

| Model | Approx. RAM | Fits in 8GB Hobby plan? |
|---|---|---|
| `nllb-200-distilled-600M` | ~2-3GB | Yes, comfortably |
| `nllb-200-distilled-1.3B` (**current**) | ~4-5GB | Yes |
| `nllb-200-1.3B` (full, non-distilled) | ~5GB | Yes, similar to distilled-1.3B |
| `nllb-200-3.3B` | ~10-13GB | **No** — would OOM-crash the whole app, not just fail a translation |

`nllb-200-3.3B` was tried and reverted during this project's development
for exactly that reason. Bemba is a low-resource language pair for NLLB,
and low-resource pairs benefit disproportionately from larger models — so
this is a real, meaningful quality ceiling, not just a "nice to have."

**To go bigger**, the only real path is upgrading past the Hobby plan.
Railway's Pro plan appears to offer up to 32GB RAM per service (worth
confirming current pricing directly on Railway's site before committing,
since plan details/pricing are exactly the kind of thing that changes over
time). At that tier, `nllb-200-3.3B` becomes a one-line `MODEL_ID` change
back, with real headroom.

### Cold starts

Both the translation model and each TTS model are loaded lazily — on the
*first request that needs them* after a deploy or restart, not at server
startup. Expect the first `/translate`, `/api/lessons`, or `/api/tts` call
after any deploy to be noticeably slower (seconds to low minutes,
depending on model size and Railway's download speed) while the model
loads into memory. Every request after that first one is fast, since the
model stays resident in the process until the next restart.

If a request seems "stuck," check Railway's live logs before assuming
something's broken — a line like `Loading MMS-TTS model for 'bem'...`
sitting there for a while is expected behavior, not an error.

## Web app

No separate deployment — served directly by the same FastAPI process from
`static/`. The only build step is Tailwind: editing `static/input.css`
requires rebuilding `static/output.css` before the change is live, and the
`?v=N` cache-busting query string on `output.css`/`auth.js` needs bumping
whenever either file changes, or browsers/CDNs may keep serving a stale
cached copy indefinitely.

## Android app

Not deployed anywhere — built locally (or via CI) into a debug APK and
installed directly on a device, or tested via Appetize.io. See
[ANDROID.md](ANDROID.md) for the full build walkthrough. `ApiConfig.BASE_URL`
points at the Railway deployment above — same backend, same database, same
accounts as the web app.
