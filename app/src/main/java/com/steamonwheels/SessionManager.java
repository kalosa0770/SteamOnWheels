package com.steamonwheels;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores the signed-in user's auth token and basic profile locally.
 * This is the Android equivalent of the token/user handling in
 * static/auth.js on the web app (which uses localStorage).
 */
public class SessionManager {

    private static final String PREFS_NAME = "steamonwheels_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ROLE = "role";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String token, int userId, String name, String email, String role) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putInt(KEY_USER_ID, userId)
                .putString(KEY_NAME, name)
                .putString(KEY_EMAIL, email)
                .putString(KEY_ROLE, role)
                .apply();
    }

    /** Updates just the profile fields, keeping the existing token (e.g. after a name change). */
    public void updateProfile(String name, String email) {
        prefs.edit()
                .putString(KEY_NAME, name)
                .putString(KEY_EMAIL, email)
                .apply();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }

    public String getName() {
        return prefs.getString(KEY_NAME, "");
    }

    public String getFirstName() {
        String name = getName();
        if (name == null || name.trim().isEmpty()) return "";
        return name.trim().split("\\s+")[0];
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, "pupil");
    }

    public boolean isTeacher() {
        return "teacher".equals(getRole());
    }
}
