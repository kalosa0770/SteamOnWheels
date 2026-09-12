# Android app — `com.steamonwheels`

Native Java app (no Kotlin), OkHttp for networking, Room for offline lesson
caching, plain `LinearLayout`/`ScrollView` XML layouts (no ConstraintLayout
usage despite it being a dependency).

## Architecture

Three layers:

1. **Service classes** (no UI) — `ApiConfig`, `SessionManager`,
   `AuthService`, `LessonService`, `TranslationService`. Every network call
   in the app goes through one of these.
2. **Activities** — one per screen, each pairing with one `activity_*.xml`.
3. **Room** — `AppDatabase` / `Lesson` / `LessonDao` (pre-existing in the
   project before this build; integrated into, not created by, this work).

### Service classes

| Class | Responsibility |
|---|---|
| `ApiConfig` | One constant: `BASE_URL`. |
| `SessionManager` | SharedPreferences wrapper for the auth token + cached user info. Equivalent to `auth.js`'s localStorage handling on web. |
| `AuthService` | `signup`, `login`, `fetchProfile`, `updateName`, `changePassword`. |
| `LessonService` | `listSubjectLessons`, `getLesson`, `createLesson`, `teacherLessons`, `translateText`, `recordProgress`, `getProgress`. |
| `TranslationService` | `translateToBemba` (legacy, mostly superseded — see below) and `fetchAudio` (TTS). |

`TranslationService.translateToBemba()` hits `POST /translate` directly and
predates the two-step upload flow. It's mostly dead code now that
`UploadLessonActivity` calls `LessonService.translateText()` instead — left
in place in case another screen wants a one-off translation later, but
nothing currently calls it.

### Activities

| Class | Screen | Role gate |
|---|---|---|
| `LoginActivity` | Log in | none (pre-auth) |
| `SignupActivity` | Sign up | none (pre-auth) |
| `MainActivity` | Home (subject grid) — **app launcher** | pupil |
| `AllLessonsActivity` | All Lessons (subject summaries) | any |
| `SubjectLessonsActivity` | One subject's lesson list | any |
| `LessonActivity` | One lesson's content + audio | any |
| `ProgressActivity` | Per-subject progress bars | pupil |
| `ProfileActivity` | Pupil profile | pupil |
| `AccountSettingsActivity` | Edit name/password | any |
| `TeacherDashboardActivity` | Teacher's posted lessons | teacher |
| `UploadLessonActivity` | Translate → review → save a lesson | teacher |

"Role gate" is enforced identically to the web app's pattern: each
Activity's `onCreate()` checks `SessionManager` (and redirects to
`LoginActivity` or the appropriate home screen) before doing anything else.

`MainActivity` is the manifest's launcher `<activity>`, but it immediately
redirects unauthenticated users to `LoginActivity` and teachers to
`TeacherDashboardActivity` — so functionally, the real entry point adapts
to whoever's actually logged in.

### Data flow: lessons

A subject can have multiple lessons ("sections"). The flow is:

```
MainActivity (subject tile)
  → SubjectLessonsActivity (list of lessons under that subject)
    → LessonActivity (one lesson, by id)
```

`LessonActivity` is **network-first with a Room fallback**: it calls
`GET /api/lessons/{id}` first; on success, it displays the content and
caches it to Room (`Lesson.id` is explicitly set to the *server's* lesson
id before insert, so `OnConflictStrategy.REPLACE` keeps the local and
server copies in sync). On network failure, it falls back to a Room lookup
for that same id, so a lesson already opened once stays readable offline.
Audio playback still requires network — it can't be cached the same way.

### Data flow: uploading a lesson

`UploadLessonActivity` is two steps, matching `upload.html` on web exactly:

1. **Translate** — `LessonService.translateText()` called once for the
   topic, once for the content, populating two editable Bemba fields.
2. **Save** — `LessonService.createLesson()` submits English *and* Bemba
   fields exactly as they read on screen (edited or not) to
   `POST /api/lessons`, which just saves what it's given — no server-side
   translation happens at save time. On success, the result is also cached
   to Room the same way `LessonActivity` does.

## File inventory

```
com/steamonwheels/
├── ApiConfig.java              SessionManager.java        AuthService.java
├── LessonService.java          TranslationService.java    NavHelper.java
├── LoginActivity.java          SignupActivity.java
├── MainActivity.java           AllLessonsActivity.java    SubjectLessonsActivity.java
├── LessonActivity.java         ProgressActivity.java      ProfileActivity.java
├── AccountSettingsActivity.java
├── TeacherDashboardActivity.java
├── UploadLessonActivity.java
├── AppDatabase.java*  Lesson.java*  LessonDao.java*        (*pre-existing, not part of this build)

res/layout/
├── activity_main.xml            activity_all_lessons.xml
├── activity_subject_lessons.xml activity_lesson.xml
├── activity_progress.xml        activity_profile.xml
├── activity_login.xml           activity_signup.xml
├── activity_account_settings.xml
├── activity_teacher_dashboard.xml
├── activity_upload_lesson.xml
├── bottom_nav.xml                          (shared <include> across pupil screens)
├── item_lesson_card.xml                    (inflated in lesson lists)
├── item_progress_card.xml                  (inflated in ProgressActivity)
└── item_subject_summary_card.xml           (inflated in AllLessonsActivity)

res/drawable/
├── bg_orange_card.xml     content panels
├── bg_lesson_card.xml     clickable lesson rows (press feedback)
├── bg_subject_card.xml    clickable subject tiles (press feedback)
├── bg_bottom_nav.xml      bottom nav bar pill
├── bg_button_nav.xml      generic navy CTA pill (Log Out, Change Password)
├── bg_button_orange.xml   generic orange CTA pill (Log in, Save, Translate)
├── bg_audio_button.xml    lesson Play button (orange flash on press)
├── bg_lang_toggle.xml     ENG/BEM toggle container
└── ic_launcher.xml        app icon (functional placeholder, not final art)

res/values/colors.xml      every color used across all of the above, named
```

## Building without Android Studio

This app was built and debugged entirely via command line (no Android
Studio installed) using PowerShell on Windows:

```powershell
cd <project-root>
.\gradlew.bat assembleDebug
```

Requirements:
- Android SDK command-line tools installed somewhere on disk (not
  necessarily via Android Studio — the "Command line tools only" download
  from developer.android.com works).
- `local.properties` in the project root pointing at it:
  ```
  sdk.dir=C:\\path\\to\\sdk
  ```
- Confirm `platforms;android-34` and `build-tools;34.0.0` (or whatever
  `compileSdk`/`targetSdk` your `app/build.gradle` specifies) are actually
  installed under that SDK path.

Output APK: `app\build\outputs\apk\debug\app-debug.apk`

### Testing without a physical device or Android Studio

- **Diawi** (diawi.com) — drag in the APK, get a link/QR code, scan with a
  real phone to install directly. No account needed. Best option if you
  have any Android phone at all, since it exercises real hardware (real
  network, real audio output) rather than an emulator.
- **Appetize.io** — runs the APK in an emulator inside your browser tab.
  No device needed at all, but has its own quirks — notably, its own
  audio can be muted independently of your system volume; check for a
  mute toggle in its interface if audio seems broken but everything else
  works.
- **GitHub Actions** — if you don't want to install the SDK locally at
  all, a CI workflow (`actions/setup-java` + `./gradlew assembleDebug` +
  `actions/upload-artifact`) builds the APK entirely in the cloud, with
  zero local setup. Slower iteration loop than building locally, but zero
  local toolchain required.

## Real bugs hit and fixed during this build (worth knowing about)

These aren't hypothetical — each one actually broke the app during
development and testing, in case similar patterns come up in future
changes:

1. **`Button` vs `TextView` field type mismatch.** Every "button" in this
   app is a styled `<TextView>` (for a consistent look across the app),
   except the lesson screen's Play button, which is a real `<Button>`.
   Several Activities had Java fields declared as `Button` for the styled
   `TextView` elements. This *compiles fine* (the compiler has no idea
   what type the XML actually inflates to) but throws a
   `ClassCastException` at runtime the instant `findViewById()` runs —
   which crashed the app immediately on launch, since `LoginActivity` (the
   very first real screen) had this bug. Fixed by matching every field's
   declared type to its actual XML element exactly. **Lesson: a successful
   Gradle build does not guarantee `findViewById()` casts are correct** —
   `R.id` values are global across the whole app, not scoped per-layout,
   so a mismatched type reference compiles even when it's wrong.

2. **Missing `android:theme` on `<application>`.** `AppCompatActivity`
   requires a `Theme.AppCompat`-descended theme; without one explicitly
   set, it throws `IllegalStateException: You need to use a Theme.AppCompat
   theme` on the very first `setContentView()` call. Caused by an early
   draft manifest (missing the theme entirely) ending up as the real
   manifest instead of a later corrected version.

3. **VectorDrawable using SVG syntax instead of Android's.** `ic_launcher.xml`
   initially used `android:viewBox` (SVG attribute name) instead of
   `android:viewportWidth`/`android:viewportHeight` (the actual
   `VectorDrawable` attributes) — a resource-linking build failure, not a
   runtime crash. AAPT2 rejected it outright rather than silently
   misrendering it.

4. **Files described in chat vs. files actually present in the repo can
   drift.** Several rounds of debugging in this project traced back to a
   file existing in conversation history but never actually being copied
   into the real project (or an old draft being copied instead of the
   final version). When something behaves like a bug that "should already
   be fixed," checking what's actually on disk is worth doing before
   assuming the fix is wrong.
