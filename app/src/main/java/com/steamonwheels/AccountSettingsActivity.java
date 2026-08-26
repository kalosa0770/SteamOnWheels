package com.steamonwheels;

import android.os.Bundle;

import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

/**
 * Android equivalent of account-settings.html: update display name, and
 * change password (requires current password to be re-entered).
 *
 * NOTE: assumes activity_account_settings.xml with EditText etName,
 * EditText etEmail (disabled/read-only), TextView btnSaveName,
 * TextView tvNameStatus, EditText etCurrentPassword, etNewPassword,
 * etConfirmNewPassword, TextView btnChangePassword, TextView tvPasswordStatus,
 * View btnBack.
 */
public class AccountSettingsActivity extends AppCompatActivity {

    private EditText etName, etEmail;
    private TextView btnSaveName;
    private TextView tvNameStatus;

    private EditText etCurrentPassword, etNewPassword, etConfirmNewPassword;
    private TextView btnChangePassword;
    private TextView tvPasswordStatus;

    private AuthService authService;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_settings);

        authService = new AuthService();
        sessionManager = new SessionManager(this);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        btnSaveName = findViewById(R.id.btnSaveName);
        tvNameStatus = findViewById(R.id.tvNameStatus);

        etCurrentPassword = findViewById(R.id.etCurrentPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        tvPasswordStatus = findViewById(R.id.tvPasswordStatus);

        etName.setText(sessionManager.getName());
        etEmail.setText(sessionManager.getEmail());
        etEmail.setEnabled(false);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSaveName.setOnClickListener(v -> saveName());
        btnChangePassword.setOnClickListener(v -> changePassword());
    }

    private void saveName() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            showStatus(tvNameStatus, "Name cannot be empty.", true);
            return;
        }

        tvNameStatus.setVisibility(TextView.GONE);
        btnSaveName.setEnabled(false);
        btnSaveName.setText("Saving…");

        authService.updateName(sessionManager.getToken(), name, new AuthService.ProfileCallback() {
            @Override
            public void onSuccess(JSONObject user) {
                runOnUiThread(() -> {
                    sessionManager.updateProfile(user.optString("name", name), sessionManager.getEmail());
                    showStatus(tvNameStatus, "Saved!", false);
                    btnSaveName.setEnabled(true);
                    btnSaveName.setText("Save name");
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    showStatus(tvNameStatus, errorMessage, true);
                    btnSaveName.setEnabled(true);
                    btnSaveName.setText("Save name");
                });
            }
        });
    }

    private void changePassword() {
        String currentPassword = etCurrentPassword.getText().toString();
        String newPassword = etNewPassword.getText().toString();
        String confirmNewPassword = etConfirmNewPassword.getText().toString();

        if (!newPassword.equals(confirmNewPassword)) {
            showStatus(tvPasswordStatus, "New passwords do not match.", true);
            return;
        }

        tvPasswordStatus.setVisibility(TextView.GONE);
        btnChangePassword.setEnabled(false);
        btnChangePassword.setText("Changing…");

        authService.changePassword(sessionManager.getToken(), currentPassword, newPassword, new AuthService.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    showStatus(tvPasswordStatus, "Password changed!", false);
                    etCurrentPassword.setText("");
                    etNewPassword.setText("");
                    etConfirmNewPassword.setText("");
                    btnChangePassword.setEnabled(true);
                    btnChangePassword.setText("Change password");
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    showStatus(tvPasswordStatus, errorMessage, true);
                    btnChangePassword.setEnabled(true);
                    btnChangePassword.setText("Change password");
                });
            }
        });
    }

    private void showStatus(TextView view, String message, boolean isError) {
        view.setText(message);
        view.setTextColor(isError ? 0xFFDC2626 : 0xFF15803D);
        view.setVisibility(TextView.VISIBLE);
    }
}