package com.steamonwheels;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * "All lessons" screen: one card per subject showing how many lessons
 * exist and the newest topic, tapping opens SubjectLessonsActivity for
 * that subject. Android equivalent of lessons.html.
 *
 * NOTE: assumes activity_all_lessons.xml with LinearLayout R.id.lessonsContainer
 * and the same bottom-nav ids (navHome/navLessons/navProgress/navProfile) used
 * elsewhere, and reuses item_subject_summary_card.xml for each row.
 */
public class AllLessonsActivity extends AppCompatActivity {

    private static final String[] SUBJECTS = {"Maths", "Literacy", "Science", "CTS"};

    private LessonService lessonService;
    private SessionManager sessionManager;
    private LinearLayout lessonsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_lessons);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        lessonService = new LessonService();
        lessonsContainer = findViewById(R.id.lessonsContainer);

        findViewById(R.id.navHome).setOnClickListener(v -> goTo(MainActivity.class));
        findViewById(R.id.navLessons).setOnClickListener(v -> { /* already here */ });
        findViewById(R.id.navProgress).setOnClickListener(v -> goTo(ProgressActivity.class));
        findViewById(R.id.navProfile).setOnClickListener(v -> goTo(ProfileActivity.class));
        NavHelper.highlightTab(this, R.id.navLessons);

        loadAllSubjects();
    }

    private void loadAllSubjects() {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String subject : SUBJECTS) {
            View card = inflater.inflate(R.layout.item_subject_summary_card, lessonsContainer, false);
            TextView tvSubject = card.findViewById(R.id.tvSubjectName);
            TextView tvSummary = card.findViewById(R.id.tvSubjectSummary);
            tvSubject.setText(subject);
            tvSummary.setText("Loading…");
            lessonsContainer.addView(card);

            card.setOnClickListener(v -> {
                Intent intent = new Intent(AllLessonsActivity.this, SubjectLessonsActivity.class);
                intent.putExtra("SUBJECT_NAME", subject);
                startActivity(intent);
            });

            lessonService.listSubjectLessons(sessionManager.getToken(), subject, new LessonService.LessonListCallback() {
                @Override
                public void onSuccess(JSONArray lessons) {
                    runOnUiThread(() -> {
                        if (lessons.length() == 0) {
                            tvSummary.setText("No lessons yet");
                            return;
                        }
                        JSONObject latest = lessons.optJSONObject(0);
                        String topic = latest != null ? latest.optString("topic_en", "") : "";
                        String countLabel = lessons.length() + (lessons.length() == 1 ? " lesson" : " lessons");
                        tvSummary.setText(topic + "  ·  " + countLabel);
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(() -> tvSummary.setText("Could not load"));
                }
            });
        }
    }

    private void goTo(Class<?> destination) {
        startActivity(new Intent(this, destination));
    }
}
