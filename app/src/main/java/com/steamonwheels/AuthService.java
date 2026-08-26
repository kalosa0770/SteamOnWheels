package com.steamonwheels;

import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Wraps the /api/auth/* endpoints: signup, login, fetching the current
 * profile, updating the display name, and changing the password.
 * Mirrors what static/auth.js + login.html/signup.html/account-settings.html
 * do on the web app.
 */
public class AuthService {

    private static final String SIGNUP_URL = ApiConfig.BASE_URL + "/api/auth/signup";
    private static final String LOGIN_URL = ApiConfig.BASE_URL + "/api/auth/login";
    private static final String ME_URL = ApiConfig.BASE_URL + "/api/auth/me";
    private static final String CHANGE_PASSWORD_URL = ApiConfig.BASE_URL + "/api/auth/change-password";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public interface AuthCallback {
        void onSuccess(String token, JSONObject user);
        void onError(String errorMessage);
    }

    public interface ProfileCallback {
        void onSuccess(JSONObject user);
        void onError(String errorMessage);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    /** POST /api/auth/signup. On success, does NOT log the user in — matches
     *  the web app's flow of sending them to log in with their new account. */
    public void signup(String name, String email, String password, String role, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("email", email);
            json.put("password", password);
            json.put("role", role);
            postForToken(SIGNUP_URL, json, callback);
        } catch (Exception e) {
            callback.onError("Request error: " + e.getMessage());
        }
    }

    /** POST /api/auth/login. */
    public void login(String email, String password, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("password", password);
            postForToken(LOGIN_URL, json, callback);
        } catch (Exception e) {
            callback.onError("Request error: " + e.getMessage());
        }
    }

    private void postForToken(String url, JSONObject body, AuthCallback callback) {
        RequestBody requestBody = RequestBody.create(body.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(requestBody).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "{}";
                try {
                    JSONObject json = new JSONObject(responseBody);
                    if (response.isSuccessful()) {
                        callback.onSuccess(json.getString("token"), json.getJSONObject("user"));
                    } else {
                        callback.onError(json.optString("detail", "Request failed (" + response.code() + ")"));
                    }
                } catch (Exception e) {
                    callback.onError("Parsing error: " + e.getMessage());
                }
            }
        });
    }

    /** GET /api/auth/me. */
    public void fetchProfile(String token, ProfileCallback callback) {
        Request request = new Request.Builder()
                .url(ME_URL)
                .get()
                .header("Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                handleProfileResponse(response, callback);
            }
        });
    }

    /** PUT /api/auth/me — updates the display name only (email isn't editable). */
    public void updateName(String token, String name, ProfileCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("name", name);
            RequestBody requestBody = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(ME_URL)
                    .put(requestBody)
                    .header("Authorization", "Bearer " + token)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    handleProfileResponse(response, callback);
                }
            });
        } catch (Exception e) {
            callback.onError("Request error: " + e.getMessage());
        }
    }

    private void handleProfileResponse(Response response, ProfileCallback callback) throws IOException {
        String responseBody = response.body() != null ? response.body().string() : "{}";
        try {
            JSONObject json = new JSONObject(responseBody);
            if (response.isSuccessful()) {
                callback.onSuccess(json);
            } else {
                callback.onError(json.optString("detail", "Request failed (" + response.code() + ")"));
            }
        } catch (Exception e) {
            callback.onError("Parsing error: " + e.getMessage());
        }
    }

    /** POST /api/auth/change-password. */
    public void changePassword(String token, String currentPassword, String newPassword, SimpleCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("current_password", currentPassword);
            json.put("new_password", newPassword);
            RequestBody requestBody = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(CHANGE_PASSWORD_URL)
                    .post(requestBody)
                    .header("Authorization", "Bearer " + token)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess();
                        return;
                    }
                    String responseBody = response.body() != null ? response.body().string() : "{}";
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        callback.onError(json.optString("detail", "Request failed (" + response.code() + ")"));
                    } catch (Exception e) {
                        callback.onError("Request failed (" + response.code() + ")");
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Request error: " + e.getMessage());
        }
    }
}
