package com.steamonwheels;

import android.content.Intent;
import android.os.Bundle;

import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

/**
 * Android equivalent of signup.html. On success, does NOT log the user in —
 * sends them to LoginActivity with their email prefilled, matching the
 * web app's "create account, then log in" flow.
 *
 * NOTE: assumes activity_signup.xml with EditText etName, etEmail,
 * etPassword, etConfirmPassword; RadioGroup rgRole with RadioButtons
 * rbPupil (checked by default) and rbTeacher; TextView btnSignup;
 * TextView tvError, tvGoToLogin.
 */
public class SignupActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword, etConfirmPassword;
    private RadioGroup rgRole;
    private TextView btnSignup;
    private TextView tvError;

    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        authService = new AuthService();

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        rgRole = findViewById(R.id.rgRole);
        btnSignup = findViewById(R.id.btnSignup);
        tvError = findViewById(R.id.tvError);

        btnSignup.setOnClickListener(v -> attemptSignup());
        findViewById(R.id.tvGoToLogin).setOnClickListener(v ->
                startActivity(new Intent(SignupActivity.this, LoginActivity.class)));
    }

    private void attemptSignup() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();
        String role = rgRole.getCheckedRadioButtonId() == R.id.rbTeacher ? "teacher" : "pupil";

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        tvError.setVisibility(TextView.GONE);
        btnSignup.setEnabled(false);
        btnSignup.setText("Creating account…");

        authService.signup(name, email, password, role, new AuthService.AuthCallback() {
            @Override
            public void onSuccess(String token, JSONObject user) {
                // Deliberately not saving the session here — the web app's
                // signup flow sends the user to log in with their new
                // credentials rather than signing them in automatically.
                runOnUiThread(() -> {
                    Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                    intent.putExtra("SIGNED_UP_EMAIL", user.optString("email", email));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    showError(errorMessage);
                    btnSignup.setEnabled(true);
                    btnSignup.setText("Create account");
                });
            }
        });
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(TextView.VISIBLE);
    }
}