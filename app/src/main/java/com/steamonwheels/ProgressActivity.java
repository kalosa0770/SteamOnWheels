package com.steamonwheels;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Shows each subject's progress (0/50/100, from GET /api/progress) as a
 * bar with a "Continue learning" button. Android equivalent of progress.html.
 *
 * NOTE: assumes activity_progress.xml with LinearLayout R.id.progressContainer
 * and the shared bottom-nav ids, and reuses item_progress_card.xml.
 */
public class ProgressActivity extends AppCompatActivity {

    private LessonService lessonService;
    private SessionManager sessionManager;
    private LinearLayout progressContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        lessonService = new LessonService();
        progressContainer = findViewById(R.id.progressContainer);

        findViewById(R.id.navHome).setOnClickListener(v -> goTo(MainActivity.class));
        findViewById(R.id.navLessons).setOnClickListener(v -> goTo(AllLessonsActivity.class));
        findViewById(R.id.navProgress).setOnClickListener(v -> { /* already here */ });
        findViewById(R.id.navProfile).setOnClickListener(v -> goTo(ProfileActivity.class));
        NavHelper.highlightTab(this, R.id.navProgress);

        loadProgress();
    }

    private void loadProgress() {
        lessonService.getProgress(sessionManager.getToken(), new LessonService.ProgressCallback() {
            @Override
            public void onSuccess(JSONArray progress) {
                runOnUiThread(() -> render(progress));
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() ->
                        Toast.makeText(ProgressActivity.this, "Could not load progress: " + errorMessage, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void render(JSONArray progress) {
        progressContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < progress.length(); i++) {
            JSONObject entry = progress.optJSONObject(i);
            if (entry == null) continue;

            String subject = entry.optString("subject", "");
            int pct = entry.optInt("pct", 0);

            View card = inflater.inflate(R.layout.item_progress_card, progressContainer, false);
            TextView tvSubject = card.findViewById(R.id.tvProgressSubject);
            ProgressBar bar = card.findViewById(R.id.progressBar);
            TextView tvPercent = card.findViewById(R.id.tvProgressPercent);
            TextView btnContinue = card.findViewById(R.id.btnContinue);

            tvSubject.setText(subject);
            bar.setMax(100);
            bar.setProgress(pct);
            tvPercent.setText(pct + "%");

            btnContinue.setOnClickListener(v -> {
                Intent intent = new Intent(ProgressActivity.this, SubjectLessonsActivity.class);
                intent.putExtra("SUBJECT_NAME", subject);
                startActivity(intent);
            });

            progressContainer.addView(card);
        }
    }

    private void goTo(Class<?> destination) {
        startActivity(new Intent(this, destination));
    }
}
