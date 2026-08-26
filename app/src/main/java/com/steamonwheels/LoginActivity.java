package com.steamonwheels;

import android.content.Intent;
import android.os.Bundle;

import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

/**
 * Android equivalent of login.html. If SIGNED_UP_EMAIL is passed in
 * (from SignupActivity, after a successful signup), prefills the email
 * field — matching the web app's ?signedUp=1&email=... flow.
 *
 * NOTE: assumes activity_login.xml with EditText etEmail, EditText etPassword,
 * TextView btnLogin, TextView tvError, TextView tvGoToSignup.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private TextView btnLogin;
    private TextView tvError;

    private AuthService authService;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authService = new AuthService();
        sessionManager = new SessionManager(this);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvError = findViewById(R.id.tvError);

        // If already logged in, skip straight past this screen.
        if (sessionManager.isLoggedIn()) {
            goToHomeForRole(sessionManager.getRole());
            return;
        }

        String signedUpEmail = getIntent().getStringExtra("SIGNED_UP_EMAIL");
        if (signedUpEmail != null) {
            etEmail.setText(signedUpEmail);
            Toast.makeText(this, "Account created — log in below to continue.", Toast.LENGTH_LONG).show();
        }

        btnLogin.setOnClickListener(v -> attemptLogin());
        findViewById(R.id.tvGoToSignup).setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, SignupActivity.class)));
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please fill in both fields.");
            return;
        }

        tvError.setVisibility(TextView.GONE);
        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in…");

        authService.login(email, password, new AuthService.AuthCallback() {
            @Override
            public void onSuccess(String token, JSONObject user) {
                runOnUiThread(() -> {
                    String role = user.optString("role", "pupil");
                    sessionManager.saveSession(
                            token,
                            user.optInt("id", -1),
                            user.optString("name", ""),
                            user.optString("email", ""),
                            role
                    );
                    goToHomeForRole(role);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    showError(errorMessage);
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Log in");
                });
            }
        });
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(TextView.VISIBLE);
    }

    private void goToHomeForRole(String role) {
        Class<?> destination = "teacher".equals(role) ? TeacherDashboardActivity.class : MainActivity.class;
        Intent intent = new Intent(this, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}