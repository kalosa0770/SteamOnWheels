package com.steamonwheels;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.concurrent.Executors;

/**
 * Teacher-only lesson upload form, in two steps:
 *   1. Translate — POST /translate on the topic and content, fills two
 *      editable Bemba fields with the result.
 *   2. Save — POST /api/lessons with the English AND Bemba fields exactly
 *      as they currently read, letting a teacher review and fix awkward
 *      phrasing before anything is saved, rather than saving whatever the
 *      model produced sight-unseen.
 *
 * Every save creates a NEW lesson (a new "section") under that subject
 * rather than overwriting anything, matching the backend.
 *
 * The server is the source of truth, but on a successful save we also cache
 * the lesson into Room (keyed by the server's returned lesson id) so it's
 * available offline the same way LessonActivity expects.
 *
 * NOTE: assumes activity_upload_lesson.xml with EditText etSubject,
 * etTopicEnglish, etContentEnglish, etTopicBemba, etContentBemba;
 * TextView btnTranslate, btnSave, tvStatus; View bemFields (a container
 * shown/hidden around the Bemba fields + Save button); and View btnBack.
 */
public class UploadLessonActivity extends AppCompatActivity {

    private EditText etSubject, etTopicEnglish, etContentEnglish;
    private EditText etTopicBemba, etContentBemba;
    private TextView btnTranslate, btnSave, tvStatus;
    private View bemFields;

    private LessonService lessonService;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_lesson);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn() || !sessionManager.isTeacher()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        lessonService = new LessonService();

        etSubject = findViewById(R.id.etSubject);
        etTopicEnglish = findViewById(R.id.etTopicEnglish);
        etContentEnglish = findViewById(R.id.etContentEnglish);
        etTopicBemba = findViewById(R.id.etTopicBemba);
        etContentBemba = findViewById(R.id.etContentBemba);
        btnTranslate = findViewById(R.id.btnTranslate);
        btnSave = findViewById(R.id.btnSave);
        tvStatus = findViewById(R.id.tvStatus);
        bemFields = findViewById(R.id.bemFields);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        btnTranslate.setOnClickListener(v -> translate());
        btnSave.setOnClickListener(v -> save());
    }

    private void translate() {
        String subject = etSubject.getText().toString().trim();
        String topicEn = etTopicEnglish.getText().toString().trim();
        String contentEn = etContentEnglish.getText().toString().trim();

        if (subject.isEmpty() || topicEn.isEmpty() || contentEn.isEmpty()) {
            Toast.makeText(this, "Please fill in subject, topic, and content first", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("Translating…");
        btnTranslate.setEnabled(false);

        lessonService.translateText(sessionManager.getToken(), topicEn, new LessonService.TranslateCallback() {
            @Override
            public void onSuccess(String translatedTopic) {
                lessonService.translateText(sessionManager.getToken(), contentEn, new LessonService.TranslateCallback() {
                    @Override
                    public void onSuccess(String translatedContent) {
                        runOnUiThread(() -> {
                            etTopicBemba.setText(translatedTopic);
                            etContentBemba.setText(translatedContent);
                            bemFields.setVisibility(View.VISIBLE);
                            tvStatus.setText("Translated — review the Bemba below, edit anything that reads awkwardly, then save.");
                            btnTranslate.setEnabled(true);
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            tvStatus.setText("Error: " + errorMessage);
                            btnTranslate.setEnabled(true);
                        });
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    tvStatus.setText("Error: " + errorMessage);
                    btnTranslate.setEnabled(true);
                });
            }
        });
    }

    private void save() {
        String subject = etSubject.getText().toString().trim();
        String topicEn = etTopicEnglish.getText().toString().trim();
        String contentEn = etContentEnglish.getText().toString().trim();
        String topicBem = etTopicBemba.getText().toString().trim();
        String contentBem = etContentBemba.getText().toString().trim();

        if (topicBem.isEmpty() || contentBem.isEmpty()) {
            Toast.makeText(this, "Bemba fields cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("Saving…");
        btnSave.setEnabled(false);

        lessonService.createLesson(sessionManager.getToken(), subject, topicEn, contentEn, topicBem, contentBem, new LessonService.LessonCallback() {
            @Override
            public void onSuccess(JSONObject lesson) {
                int serverId = lesson.optInt("id", 0);
                cacheLessonLocally(serverId, subject, topicEn, topicBem, contentEn, contentBem);

                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    tvStatus.setText("Saved to " + subject + "!");
                    Toast.makeText(UploadLessonActivity.this, "Saved to " + subject + "!", Toast.LENGTH_LONG).show();

                    // Reset the form so the teacher can post another section
                    // for the same or a different subject right away.
                    etTopicEnglish.setText("");
                    etContentEnglish.setText("");
                    etTopicBemba.setText("");
                    etContentBemba.setText("");
                    bemFields.setVisibility(View.GONE);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    tvStatus.setText("Error: " + errorMessage);
                    Toast.makeText(UploadLessonActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** Caches the newly-created lesson into Room, keyed by the server's
     *  lesson id, so LessonActivity can find it offline later. */
    private void cacheLessonLocally(int serverId, String subject, String topicEn, String topicBem, String contentEn, String contentBem) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            Lesson lesson = new Lesson(subject, topicEn, topicBem, contentEn, contentBem);
            lesson.id = serverId;
            db.lessonDao().insertLesson(lesson);
        });
    }
}