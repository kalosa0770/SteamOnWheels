package com.steamonwheels;

import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;

public class LessonActivity extends AppCompatActivity {

    private boolean isBemba = true;
    private MediaPlayer mediaPlayer;
    private TranslationService translationService;

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

        translationService = new TranslationService();

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

        btnBack.setOnClickListener(v -> finish());
        findViewById(R.id.btnLessonLangToggle).setOnClickListener(v -> {
            isBemba = !isBemba;
            updateLessonDisplay();
        });

        btnListenAudio.setOnClickListener(v -> playAudioFromRailway());

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

    private void playAudioFromRailway() {
        String speechText = tvLessonBody.getText().toString();
        String langCode = isBemba ? "bem" : "eng";

        btnListenAudio.setEnabled(false);
        Toast.makeText(this, "Generating speech from server...", Toast.LENGTH_SHORT).show();

        translationService.fetchAudio(speechText, langCode, new TranslationService.AudioCallback() {
            @Override
            public void onSuccess(byte[] audioBytes) {
                runOnUiThread(() -> {
                    btnListenAudio.setEnabled(true);
                    playWavBytes(audioBytes);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    btnListenAudio.setEnabled(true);
                    Toast.makeText(LessonActivity.this, "Audio Error: " + errorMessage, Toast.LENGTH_LONG).show();
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