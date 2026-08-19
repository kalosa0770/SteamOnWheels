package com.steamonwheels;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.Executors;

public class UploadLessonActivity extends AppCompatActivity {

    private EditText etSubject, etTopicEnglish, etContentEnglish;
    private Button btnUploadTranslate;
    private TextView tvAutoTranslateStatus;
    private TranslationService translationService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_lesson);

        etSubject = findViewById(R.id.etSubject);
        etTopicEnglish = findViewById(R.id.etTopicEnglish);
        etContentEnglish = findViewById(R.id.etContentEnglish);
        btnUploadTranslate = findViewById(R.id.btnUploadTranslate);
        tvAutoTranslateStatus = findViewById(R.id.tvAutoTranslateStatus);

        translationService = new TranslationService();

        btnUploadTranslate.setOnClickListener(v -> processUploadAndTranslate());
    }

    private void processUploadAndTranslate() {
        String subject = etSubject.getText().toString().trim();
        String topicEn = etTopicEnglish.getText().toString().trim();
        String contentEn = etContentEnglish.getText().toString().trim();

        if (subject.isEmpty() || topicEn.isEmpty() || contentEn.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        tvAutoTranslateStatus.setText("AI Status: Translating topic and content to Bemba...");
        btnUploadTranslate.setEnabled(false);

        // 1. Translate Content
        translationService.translateToBemba(contentEn, new TranslationService.TranslationCallback() {
            @Override
            public void onSuccess(String translatedContentBemba) {
                // 2. Translate Topic
                translationService.translateToBemba(topicEn, new TranslationService.TranslationCallback() {
                    @Override
                    public void onSuccess(String translatedTopicBemba) {
                        // 3. Save to Room Database
                        saveToLocalDatabase(subject, topicEn, translatedTopicBemba, contentEn, translatedContentBemba);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        showError(errorMessage);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                showError(errorMessage);
            }
        });
    }

    private void saveToLocalDatabase(String subject, String topicEn, String topicBem, String contentEn, String contentBem) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            Lesson lesson = new Lesson(subject, topicEn, topicBem, contentEn, contentBem);
            db.lessonDao().insertLesson(lesson);

            runOnUiThread(() -> {
                btnUploadTranslate.setEnabled(true);
                tvAutoTranslateStatus.setText("Saved to DB!\n\nBemba Topic: " + topicBem + "\n\nBemba Content:\n" + contentBem);
                Toast.makeText(UploadLessonActivity.this, "Saved to " + subject + "! Check the lesson screen.", Toast.LENGTH_LONG).show();
            });
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            btnUploadTranslate.setEnabled(true);
            tvAutoTranslateStatus.setText("Error: " + message);
            Toast.makeText(UploadLessonActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }
}