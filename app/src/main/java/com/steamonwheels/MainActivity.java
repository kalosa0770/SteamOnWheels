package com.steamonwheels;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private boolean isBemba = true;

    private TextView tvGreeting;
    private TextView tvSubjectsTitle;
    private TextView tvAllLessons;
    private TextView tvLangEng;
    private TextView tvLangBem;
    private View btnLanguageToggle;

    private Button btnMaths, btnLiteracy, btnScience, btnCTS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvGreeting = findViewById(R.id.tvGreeting);
        tvSubjectsTitle = findViewById(R.id.tvSubjectsTitle);
        tvAllLessons = findViewById(R.id.tvAllLessons);
        tvLangEng = findViewById(R.id.tvLangEng);
        tvLangBem = findViewById(R.id.tvLangBem);
        btnLanguageToggle = findViewById(R.id.btnLanguageToggle);

        btnMaths = findViewById(R.id.btnMaths);
        btnLiteracy = findViewById(R.id.btnLiteracy);
        btnScience = findViewById(R.id.btnScience);
        btnCTS = findViewById(R.id.btnCTS);

        btnLanguageToggle.setOnClickListener(v -> toggleLanguage());

        // Header tap -> Open Upload & Auto-Translate
        findViewById(R.id.tvAppLogo).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, UploadLessonActivity.class);
            startActivity(intent);
        });

        // Subject Click Handlers
        btnScience.setOnClickListener(v -> openSubjectLesson("Science"));
        btnMaths.setOnClickListener(v -> openSubjectLesson("Maths"));
        btnLiteracy.setOnClickListener(v -> openSubjectLesson("Literacy"));
        btnCTS.setOnClickListener(v -> openSubjectLesson("CTS"));
    }

    private void openSubjectLesson(String subjectName) {
        Intent intent = new Intent(MainActivity.this, LessonActivity.class);
        intent.putExtra("SUBJECT_NAME", subjectName);
        startActivity(intent);
    }

    private void toggleLanguage() {
        isBemba = !isBemba;

        if (isBemba) {
            tvGreeting.setText("Mwabweleni,\nba Micheal");
            tvSubjectsTitle.setText("SUBJECTS");
            tvAllLessons.setText("Amasambililo yonse  ›");
            tvLangBem.setTextColor(Color.parseColor("#FF7700"));
            tvLangEng.setTextColor(Color.parseColor("#64748B"));
        } else {
            tvGreeting.setText("Welcome,\nMicheal");
            tvSubjectsTitle.setText("SUBJECTS");
            tvAllLessons.setText("All lessons  ›");
            tvLangEng.setTextColor(Color.parseColor("#FF7700"));
            tvLangBem.setTextColor(Color.parseColor("#64748B"));
        }
    }
}