package com.steamonwheels;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Pupil home screen: greets them by name and shows the four subject tiles.
 * Android equivalent of index.html. Pupil-only — teachers are redirected
 * to TeacherDashboardActivity.
 */
public class MainActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private TextView tvGreeting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goTo(LoginActivity.class, true);
            return;
        }
        if (sessionManager.isTeacher()) {
            goTo(TeacherDashboardActivity.class, true);
            return;
        }

        tvGreeting = findViewById(R.id.tvGreeting);
        tvGreeting.setText("Welcome back,\n" + sessionManager.getFirstName());

        findViewById(R.id.btnSubjectMaths).setOnClickListener(v -> openSubject("Maths"));
        findViewById(R.id.btnSubjectLiteracy).setOnClickListener(v -> openSubject("Literacy"));
        findViewById(R.id.btnSubjectScience).setOnClickListener(v -> openSubject("Science"));
        findViewById(R.id.btnSubjectCts).setOnClickListener(v -> openSubject("CTS"));

        findViewById(R.id.navHome).setOnClickListener(v -> { /* already here */ });
        findViewById(R.id.navLessons).setOnClickListener(v -> goTo(AllLessonsActivity.class, false));
        findViewById(R.id.navProgress).setOnClickListener(v -> goTo(ProgressActivity.class, false));
        findViewById(R.id.navProfile).setOnClickListener(v -> goTo(ProfileActivity.class, false));
        NavHelper.highlightTab(this, R.id.navHome);
    }

    private void openSubject(String subject) {
        Intent intent = new Intent(this, SubjectLessonsActivity.class);
        intent.putExtra("SUBJECT_NAME", subject);
        startActivity(intent);
    }

    private void goTo(Class<?> destination, boolean clearStack) {
        Intent intent = new Intent(this, destination);
        if (clearStack) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        startActivity(intent);
        if (clearStack) finish();
    }
}
