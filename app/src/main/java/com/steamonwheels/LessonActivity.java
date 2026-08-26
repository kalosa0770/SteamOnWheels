package com.steamonwheels;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Shows a single lesson, fetched by id — NOT by subject anymore. A subject
 * can now have multiple lessons ("sections"), so the id has to come from
 * wherever the user picked this specific lesson (SubjectLessonsActivity's
 * list, or a teacher's dashboard preview button).
 *
 * Network-first: fetches from the server and caches the result locally via
 * Room (keyed by the server's lesson id, see cacheLessonLocally). If the
 * network call fails, falls back to whatever's cached in Room for that id,
 * so a pupil who already opened a lesson once can still read/hear it offline.
 *
 * Pass the lesson id via intent extra LESSON_ID (int).
 */
public class LessonActivity extends AppCompatActivity {

    private boolean isBemba = true;
    private MediaPlayer mediaPlayer;
    private TranslationService translationService;
    private LessonService lessonService;
    private SessionManager sessionManager;

    private TextView tvLessonSubject, tvLessonTopic, tvLessonBody;
    private TextView tvLessonLangEng, tvLessonLangBem, btnBack;
    private Button btnListenAudio;

    private int lessonId = -1;
    private String currentSubject = "";
    private String topicEn = "";
    private String topicBem = "";
    private String contentEn = "";
    private String contentBem = "";
    private boolean lessonLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lesson);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        translationService = new TranslationService();
        lessonService = new LessonService();

        lessonId = getIntent().getIntExtra("LESSON_ID", -1);

        tvLessonSubject = findViewById(R.id.tvLessonSubject);
        tvLessonTopic = findViewById(R.id.tvLessonTopic);
        tvLessonBody = findViewById(R.id.tvLessonBody);
        tvLessonLangEng = findViewById(R.id.tvLessonLangEng);
        tvLessonLangBem = findViewById(R.id.tvLessonLangBem);
        btnBack = findViewById(R.id.btnBack);
        btnListenAudio = findViewById(R.id.btnListenAudio);

        btnBack.setOnClickListener(v -> finish());
        findViewById(R.id.btnLessonLangToggle).setOnClickListener(v -> {
            isBemba = !isBemba;
            updateLessonDisplay();
        });

        btnListenAudio.setOnClickListener(v -> playAudioFromRailway());

        if (lessonId == -1) {
            currentSubject = "Lesson";
            topicEn = "Lesson not found";
            topicBem = "Tapali ilyashi";
            contentEn = "No lesson was specified. Please go back and pick a lesson.";
            contentBem = "Tapali ilyashi lyalangwa. Ambukileni ku numa mukasale ilyashi.";
            updateLessonDisplay();
        } else {
            loadLessonFromServer();
        }
    }

    private void loadLessonFromServer() {
        lessonService.getLesson(sessionManager.getToken(), lessonId, new LessonService.LessonCallback() {
            @Override
            public void onSuccess(JSONObject lesson) {
                currentSubject = lesson.optString("subject", "Lesson");
                topicEn = lesson.optString("topic_en", "");
                topicBem = lesson.optString("topic_bem", "");
                contentEn = lesson.optString("content_en", "");
                contentBem = lesson.optString("content_bem", "");
                lessonLoaded = true;

                cacheLessonLocally();

                runOnUiThread(() -> {
                    updateLessonDisplay();
                    recordProgress("viewed");
                });
            }

            @Override
            public void onError(String errorMessage) {
                loadLessonFromLocalCache();
            }
        });
    }

    /** Saves the just-fetched lesson to Room, keyed by the server's lesson
     *  id, so it's readable offline next time. */
    private void cacheLessonLocally() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            Lesson lesson = new Lesson(currentSubject, topicEn, topicBem, contentEn, contentBem);
            lesson.id = lessonId; // reuse the server id as the Room primary key
            db.lessonDao().insertLesson(lesson);
        });
    }

    /** Network fetch failed — look for a cached copy of this exact lesson id.
     *  LessonDao has no "get by id" query, so this filters getAllLessons()
     *  in memory; fine at this app's scale. */
    private void loadLessonFromLocalCache() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            List<Lesson> allLessons = db.lessonDao().getAllLessons();

            Lesson match = null;
            for (Lesson candidate : allLessons) {
                if (candidate.id == lessonId) {
                    match = candidate;
                    break;
                }
            }
            final Lesson found = match;

            runOnUiThread(() -> {
                if (found != null) {
                    currentSubject = found.subject;
                    topicEn = found.topicEnglish;
                    topicBem = found.topicBemba;
                    contentEn = found.contentEnglish;
                    contentBem = found.contentBemba;
                    lessonLoaded = true;
                    Toast.makeText(LessonActivity.this, "Showing an offline copy of this lesson.", Toast.LENGTH_SHORT).show();
                } else {
                    currentSubject = "Lesson";
                    topicEn = "Could not load lesson";
                    topicBem = "Tafwile ukusanga amasambililo";
                    contentEn = "There was a problem reaching the server, and no offline copy of this lesson is saved yet.";
                    contentBem = "Kwaliba ubwafya bwa kuya ku seva, kabili tapali kopi yalibikwa pa foni.";
                }
                updateLessonDisplay();
            });
        });
    }

    /** Fire-and-forget: the server silently no-ops this for teacher previews. */
    private void recordProgress(String event) {
        if (!lessonLoaded || currentSubject.isEmpty()) return;
        lessonService.recordProgress(sessionManager.getToken(), currentSubject, event, null);
    }

    private void updateLessonDisplay() {
        tvLessonSubject.setText(currentSubject);

        if (isBemba) {
            tvLessonTopic.setText(topicBem);
            tvLessonBody.setText(contentBem);
            btnListenAudio.setText("Play - Kutikeni ku fyebo");
            tvLessonLangBem.setTextColor(ContextCompat.getColor(this, R.color.white));
            tvLessonLangBem.setBackgroundResource(R.drawable.bg_button_orange);
            tvLessonLangEng.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            tvLessonLangEng.setBackgroundResource(android.R.color.transparent);
        } else {
            tvLessonTopic.setText(topicEn);
            tvLessonBody.setText(contentEn);
            btnListenAudio.setText("Play - Listen to Lesson");
            tvLessonLangEng.setTextColor(ContextCompat.getColor(this, R.color.white));
            tvLessonLangEng.setBackgroundResource(R.drawable.bg_button_orange);
            tvLessonLangBem.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            tvLessonLangBem.setBackgroundResource(android.R.color.transparent);
        }
    }

    private void playAudioFromRailway() {
        String speechText = tvLessonBody.getText().toString();
        String langCode = isBemba ? "bem" : "eng";

        btnListenAudio.setEnabled(false);
        Toast.makeText(this, "Generating speech from server...", Toast.LENGTH_SHORT).show();

        translationService.fetchAudio(sessionManager.getToken(), speechText, langCode, new TranslationService.AudioCallback() {
            @Override
            public void onSuccess(byte[] audioBytes) {
                runOnUiThread(() -> {
                    btnListenAudio.setEnabled(true);
                    playWavBytes(audioBytes);
                    recordProgress("listened");
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    btnListenAudio.setEnabled(true);
                    if (errorMessage.contains("401")) {
                        Toast.makeText(LessonActivity.this, "Your session expired — please log in again.", Toast.LENGTH_LONG).show();
                        sessionManager.clearSession();
                        startActivity(new Intent(LessonActivity.this, LoginActivity.class));
                        finish();
                    } else {
                        Toast.makeText(LessonActivity.this, "Audio unavailable offline: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void playWavBytes(byte[] audioBytes) {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            }

            File tempAudio = File.createTempFile("tts_audio", ".wav", getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempAudio);
            fos.write(audioBytes);
            fos.close();

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(tempAudio.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();

            mediaPlayer.setOnCompletionListener(mp -> tempAudio.delete());
        } catch (IOException e) {
            Toast.makeText(this, "Playback failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}
