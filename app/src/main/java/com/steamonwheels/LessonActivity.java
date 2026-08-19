package com.steamonwheels;

import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class LessonActivity extends AppCompatActivity {

    private boolean isBemba = true;
    private TextToSpeech textToSpeech;

    private TextView tvLessonSubject, tvLessonTopic, tvLessonBody;
    private TextView tvLessonLangEng, tvLessonLangBem, btnBack;
    private Button btnListenAudio;

    private String currentSubject = "Science";
    private String topicEn = "Domestic and Wild Animals";
    private String topicBem = "Inama sha mu Ng'anda ne sha mu mpanga";
    private String contentEn = "Animals are living things.\n- Cow\n- Lion\n- Elephant";
    private String contentBem = "Inama fintu ifya mweo.\n- Ing'ombe\n- Nkalamu\n- Insofu";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lesson);

        String passedSubject = getIntent().getStringExtra("SUBJECT_NAME");
        if (passedSubject != null && !passedSubject.isEmpty()) {
            currentSubject = passedSubject;
        }

        tvLessonSubject = findViewById(R.id.tvLessonSubject);
        tvLessonTopic = findViewById(R.id.tvLessonTopic);
        tvLessonBody = findViewById(R.id.tvLessonBody);
        tvLessonLangEng = findViewById(R.id.tvLessonLangEng);
        tvLessonLangBem = findViewById(R.id.tvLessonLangBem);
        btnBack = findViewById(R.id.btnBack);
        btnListenAudio = findViewById(R.id.btnListenAudio);

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });

        btnBack.setOnClickListener(v -> finish());
        findViewById(R.id.btnLessonLangToggle).setOnClickListener(v -> {
            isBemba = !isBemba;
            updateLessonDisplay();
        });

        btnListenAudio.setOnClickListener(v -> playAudio());

        // Fetch latest saved lesson for this subject from SQLite/Room DB
        loadLessonFromDatabase();
    }

    private void loadLessonFromDatabase() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            List<Lesson> lessons = db.lessonDao().getLessonsBySubject(currentSubject);

            if (!lessons.isEmpty()) {
                Lesson latestLesson = lessons.get(0);
                topicEn = latestLesson.topicEnglish;
                topicBem = latestLesson.topicBemba;
                contentEn = latestLesson.contentEnglish;
                contentBem = latestLesson.contentBemba;
            }

            runOnUiThread(this::updateLessonDisplay);
        });
    }

    private void updateLessonDisplay() {
        tvLessonSubject.setText(currentSubject);

        if (isBemba) {
            tvLessonTopic.setText(topicBem);
            tvLessonBody.setText(contentBem);
            btnListenAudio.setText("Play - Kutikeni ku fyebo");
            tvLessonLangBem.setTextColor(Color.parseColor("#FF7700"));
            tvLessonLangEng.setTextColor(Color.parseColor("#64748B"));
        } else {
            tvLessonTopic.setText(topicEn);
            tvLessonBody.setText(contentEn);
            btnListenAudio.setText("Play - Listen to Lesson");
            tvLessonLangEng.setTextColor(Color.parseColor("#FF7700"));
            tvLessonLangBem.setTextColor(Color.parseColor("#64748B"));
        }
    }

    private void playAudio() {
        String speechText = tvLessonBody.getText().toString();
        Toast.makeText(this, "Playing Audio...", Toast.LENGTH_SHORT).show();
        textToSpeech.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "LessonTTS");
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}