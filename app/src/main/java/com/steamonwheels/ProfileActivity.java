package com.steamonwheels;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Pupil profile screen: initials avatar, name, email, and links to
 * Account Settings / Recorded Lessons / Log out. Android equivalent
 * of profile.html.
 *
 * NOTE: assumes activity_profile.xml with TextView R.id.tvAvatarInitials,
 * tvProfileName, tvProfileEmail; View btnAccountSettings, btnRecordedLessons,
 * btnLogout; and the shared bottom-nav ids.
 */
public class ProfileActivity extends AppCompatActivity {

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        TextView tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        TextView tvProfileName = findViewById(R.id.tvProfileName);
        TextView tvProfileEmail = findViewById(R.id.tvProfileEmail);

        tvAvatarInitials.setText(initialsFor(sessionManager.getName()));
        tvProfileName.setText(sessionManager.getName());
        tvProfileEmail.setText(sessionManager.getEmail());

        findViewById(R.id.btnAccountSettings).setOnClickListener(v ->
                startActivity(new Intent(this, AccountSettingsActivity.class)));
        findViewById(R.id.btnRecordedLessons).setOnClickListener(v ->
                startActivity(new Intent(this, AllLessonsActivity.class)));
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());

        findViewById(R.id.navHome).setOnClickListener(v -> goTo(MainActivity.class));
        findViewById(R.id.navLessons).setOnClickListener(v -> goTo(AllLessonsActivity.class));
        findViewById(R.id.navProgress).setOnClickListener(v -> goTo(ProgressActivity.class));
        findViewById(R.id.navProfile).setOnClickListener(v -> { /* already here */ });
        NavHelper.highlightTab(this, R.id.navProfile);
    }

    private String initialsFor(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            if (!parts[i].isEmpty()) initials.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return initials.toString();
    }

    private void logout() {
        sessionManager.clearSession();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goTo(Class<?> destination) {
        startActivity(new Intent(this, destination));
    }
}
