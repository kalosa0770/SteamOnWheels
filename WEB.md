# Web app — `static/`

Plain HTML + vanilla JS + Tailwind CSS, served directly by `app.py` via
`FileResponse` (see the page routes in [BACKEND.md](BACKEND.md)). No build
step for the HTML/JS itself — only the CSS needs building.

## Pages

| File | Route | Who | What it does |
|---|---|---|---|
| `login.html` | `/login` | anyone | Log in. Prefills email + shows a confirmation banner if arriving via `?signedUp=1&email=...` from signup. |
| `signup.html` | `/signup` | anyone | Create an account (pupil or teacher — a two-way toggle). On success, redirects to `/login` rather than auto-logging-in. |
| `index.html` | `/` | pupil | Home: greeting with the pupil's real name, 4 subject tiles. |
| `lessons.html` | `/lessons` | any | "All Lessons": one card per subject, showing lesson count + newest topic, linking to `subject.html`. |
| `subject.html` | `/subject?subject=X` | any | Every lesson ("section") under one subject, newest first. |
| `lesson.html` | `/lesson?id=N` | any | One lesson's full content, EN/BEM toggle, Play audio button. Records progress on view/listen. |
| `progress.html` | `/progress` | pupil | Real per-subject progress bars from `GET /api/progress`. |
| `profile.html` | `/profile` | pupil | Name/email/avatar, links to settings, logout. |
| `account-settings.html` | `/account-settings` | any | Edit display name, change password. |
| `teacher-dashboard.html` | `/teacher/dashboard` | teacher | Every lesson the teacher's posted, grouped by subject. |
| `upload.html` | `/upload` | teacher | Two-step upload: Translate (fills editable Bemba fields) → Save. |

## Access control

Pages don't gate access server-side at the route level — `GET /lesson`
serves the HTML file to anyone who requests it. The actual gate is
client-side: every protected page's script calls `requireAuth(role)` from
`auth.js` on load, which hits `GET /api/auth/me` and redirects to `/login`
if that fails. The *data* underneath (the actual lesson content, via
`GET /api/lessons/{id}`) is properly server-side protected — a logged-out
user can load the empty page shell, but can't fetch anything real.

`requireAuth(role)`:
- `requireAuth('pupil')` — pupil-only pages (`index.html`, `progress.html`,
  `profile.html`)
- `requireAuth('teacher')` — teacher-only pages (`upload.html`,
  `teacher-dashboard.html`)
- `requireAuth()` (no argument) — any logged-in user (`lesson.html`,
  `lessons.html`, `subject.html`, `account-settings.html`) — teachers can
  preview lessons this way without it counting as pupil "progress."

## `auth.js` — shared across every page

- `saveSession(token, user)` / `clearSession()` — localStorage wrapper.
- `authFetch(url, options)` — `fetch()` wrapper that attaches
  `Authorization: Bearer <token>` and redirects to `/login` on a 401.
- `requireAuth(role)` — the page guard described above.
- `setupPasswordToggle(inputId, btnId, iconId)` — the show/hide eye icon on
  every password field across login/signup/account-settings.

## Styling

`input.css` (Tailwind source, with `@layer components` for the reusable
classes — `sidebar-item`, `lang-toggle`, `progress-card`, etc.) compiles to
`output.css`, which is what every page actually links (with a `?v=` cache-
busting query string bumped whenever the CSS changes — browsers/CDNs will
otherwise happily serve a stale cached copy indefinitely).

**Editing `input.css` requires rebuilding `output.css`** via your Tailwind
CLI before the change takes effect — there's no watch/build process wired
into the backend itself.

## Known gotcha: cache-busting

Both `output.css` and `auth.js` are referenced with a `?v=N` query string
on every page. If you edit either file and forget to bump that number,
browsers (and any CDN in front of the app) may keep serving the old
cached version indefinitely — this caused real confusion earlier in
development (a `ReferenceError` for a function that had, in fact, already
been added to `auth.js`, just not to the cached copy the browser was
still running). Bump the version number whenever you change either file.
