package com.steamonwheels;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Lists every lesson ("section") posted for one subject, newest first.
 * Launched from Home's subject tiles and from the "All lessons" screen.
 * Android equivalent of subject.html on the web app.
 *
 * Expects intent extra SUBJECT_NAME (String).
 *
 * NOTE: This assumes an activity_subject_lessons.xml layout with:
 *   - TextView R.id.tvSubjectTitle
 *   - View R.id.btnBack
 *   - LinearLayout R.id.lessonsContainer (vertical) to hold generated cards
 *   - TextView R.id.tvEmptyState (hidden by default)
 * and reuses the same lang-toggle pattern (tvLangEng/tvLangBem +
 * btnLangToggle) as LessonActivity's layout, if you want an EN/BEM switch
 * for the topic titles shown here.
 */
public class SubjectLessonsActivity extends AppCompatActivity {

    private String subject = "";
    private boolean isBemba = true;
    private JSONArray lessons = new JSONArray();

    private LessonService lessonService;
    private SessionManager sessionManager;

    private TextView tvSubjectTitle;
    private TextView tvEmptyState;
    private LinearLayout lessonsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_lessons);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        lessonService = new LessonService();
        subject = getIntent().getStringExtra("SUBJECT_NAME");
        if (subject == null) subject = "";

        tvSubjectTitle = findViewById(R.id.tvSubjectTitle);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        lessonsContainer = findViewById(R.id.lessonsContainer);

        tvSubjectTitle.setText(subject);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadLessons();
    }

    private void loadLessons() {
        lessonService.listSubjectLessons(sessionManager.getToken(), subject, new LessonService.LessonListCallback() {
            @Override
            public void onSuccess(JSONArray result) {
                lessons = result;
                runOnUiThread(SubjectLessonsActivity.this::render);
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    Toast.makeText(SubjectLessonsActivity.this, "Could not load lessons: " + errorMessage, Toast.LENGTH_LONG).show();
                    render();
                });
            }
        });
    }

    private void render() {
        lessonsContainer.removeAllViews();

        if (lessons.length() == 0) {
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }
        tvEmptyState.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < lessons.length(); i++) {
            JSONObject lesson = lessons.optJSONObject(i);
            if (lesson == null) continue;

            View card = inflater.inflate(R.layout.item_lesson_card, lessonsContainer, false);
            TextView tvTopic = card.findViewById(R.id.tvCardTopic);
            TextView tvDate = card.findViewById(R.id.tvCardDate);

            String topic = isBemba ? lesson.optString("topic_bem") : lesson.optString("topic_en");
            tvTopic.setText(topic);
            tvDate.setText(lesson.optString("created_at", ""));

            int lessonId = lesson.optInt("id", -1);
            card.setOnClickListener(v -> {
                Intent intent = new Intent(SubjectLessonsActivity.this, LessonActivity.class);
                intent.putExtra("LESSON_ID", lessonId);
                startActivity(intent);
            });

            lessonsContainer.addView(card);
        }
    }
}
