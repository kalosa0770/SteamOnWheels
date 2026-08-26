package com.steamonwheels;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Teacher's home screen: greets them by name, lists every lesson they've
 * posted grouped by subject, and links to UploadLessonActivity. Android
 * equivalent of teacher-dashboard.html.
 *
 * NOTE: assumes activity_teacher_dashboard.xml with:
 *   - TextView R.id.tvGreeting
 *   - View R.id.btnUpload ("+ New lesson")
 *   - View R.id.btnLogout
 *   - LinearLayout R.id.lessonsContainer (vertical)
 *   - TextView R.id.tvEmptyState (hidden by default)
 * and reuses item_lesson_card.xml (see SubjectLessonsActivity) for each row,
 * plus a plain subject-heading TextView created in code below.
 */
public class TeacherDashboardActivity extends AppCompatActivity {

    private LessonService lessonService;
    private SessionManager sessionManager;

    private TextView tvGreeting;
    private TextView tvEmptyState;
    private LinearLayout lessonsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_dashboard);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn() || !sessionManager.isTeacher()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        lessonService = new LessonService();

        tvGreeting = findViewById(R.id.tvGreeting);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        lessonsContainer = findViewById(R.id.lessonsContainer);

        tvGreeting.setText("Welcome back, " + sessionManager.getFirstName());

        findViewById(R.id.btnUpload).setOnClickListener(v ->
                startActivity(new Intent(TeacherDashboardActivity.this, UploadLessonActivity.class)));
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());

        loadLessons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh in case the teacher just came back from posting a new lesson.
        loadLessons();
    }

    private void loadLessons() {
        lessonService.teacherLessons(sessionManager.getToken(), new LessonService.LessonListCallback() {
            @Override
            public void onSuccess(JSONArray lessons) {
                runOnUiThread(() -> render(lessons));
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() ->
                        Toast.makeText(TeacherDashboardActivity.this, "Could not load lessons: " + errorMessage, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void render(JSONArray lessons) {
        lessonsContainer.removeAllViews();

        if (lessons.length() == 0) {
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }
        tvEmptyState.setVisibility(View.GONE);

        // Group the flat list into subject -> lessons[], preserving the
        // subject-then-newest-first order the backend already returns.
        Map<String, JSONArray> bySubject = new LinkedHashMap<>();
        for (int i = 0; i < lessons.length(); i++) {
            JSONObject lesson = lessons.optJSONObject(i);
            if (lesson == null) continue;
            String subject = lesson.optString("subject", "");
            bySubject.computeIfAbsent(subject, s -> new JSONArray()).put(lesson);
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Map.Entry<String, JSONArray> entry : bySubject.entrySet()) {
            TextView heading = new TextView(this);
            heading.setText(entry.getKey() + " (" + entry.getValue().length() + ")");
            heading.setTextSize(13);
            heading.setPadding(0, 32, 0, 8);
            lessonsContainer.addView(heading);

            JSONArray subjectLessons = entry.getValue();
            for (int i = 0; i < subjectLessons.length(); i++) {
                JSONObject lesson = subjectLessons.optJSONObject(i);
                if (lesson == null) continue;

                View card = inflater.inflate(R.layout.item_lesson_card, lessonsContainer, false);
                TextView tvTopic = card.findViewById(R.id.tvCardTopic);
                TextView tvDate = card.findViewById(R.id.tvCardDate);
                tvTopic.setText(lesson.optString("topic_en", ""));
                tvDate.setText("Posted " + lesson.optString("created_at", ""));

                int lessonId = lesson.optInt("id", -1);
                card.setOnClickListener(v -> {
                    Intent intent = new Intent(TeacherDashboardActivity.this, LessonActivity.class);
                    intent.putExtra("LESSON_ID", lessonId);
                    startActivity(intent);
                });

                lessonsContainer.addView(card);
            }
        }
    }

    private void logout() {
        sessionManager.clearSession();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
