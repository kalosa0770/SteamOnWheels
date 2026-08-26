package com.steamonwheels;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

/**
 * Everything about subjects, lessons (sections), and progress that isn't
 * translation or text-to-speech (that's TranslationService). Every call
 * here requires the pupil or teacher to be logged in — pass the token
 * from SessionManager.getToken().
 */
public class LessonService {

    private static final String LESSONS_URL = ApiConfig.BASE_URL + "/api/lessons";
    private static final String SUBJECTS_URL = ApiConfig.BASE_URL + "/api/subjects";
    private static final String TEACHER_LESSONS_URL = ApiConfig.BASE_URL + "/api/teacher/lessons";
    private static final String TRANSLATE_URL = ApiConfig.BASE_URL + "/translate";
    private static final String PROGRESS_URL = ApiConfig.BASE_URL + "/api/progress";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public interface LessonListCallback {
        void onSuccess(JSONArray lessons);
        void onError(String errorMessage);
    }

    public interface LessonCallback {
        void onSuccess(JSONObject lesson);
        void onError(String errorMessage);
    }

    public interface ProgressCallback {
        void onSuccess(JSONArray progress);
        void onError(String errorMessage);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    public interface TranslateCallback {
        void onSuccess(String translatedText);
        void onError(String errorMessage);
    }

    /** GET /api/subjects/{subject}/lessons — every lesson posted for a subject, newest first. */
    public void listSubjectLessons(String token, String subject, LessonListCallback callback) {
        String url = SUBJECTS_URL + "/" + encode(subject) + "/lessons";
        Request request = new Request.Builder()
                .url(url)
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
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Request failed (" + response.code() + ")");
                    return;
                }
                try {
                    callback.onSuccess(new JSONArray(response.body().string()));
                } catch (Exception e) {
                    callback.onError("Parsing error: " + e.getMessage());
                }
            }
        });
    }

    /** GET /api/lessons/{lessonId} — one specific lesson, full content. */
    public void getLesson(String token, int lessonId, LessonCallback callback) {
        String url = LESSONS_URL + "/" + lessonId;
        Request request = new Request.Builder()
                .url(url)
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
                String responseBody = response.body() != null ? response.body().string() : "{}";
                try {
                    if (response.isSuccessful()) {
                        callback.onSuccess(new JSONObject(responseBody));
                    } else {
                        JSONObject error = new JSONObject(responseBody);
                        callback.onError(error.optString("detail", "Lesson not found"));
                    }
                } catch (Exception e) {
                    callback.onError("Parsing error: " + e.getMessage());
                }
            }
        });
    }

    /** POST /translate — teacher only. Runs the NLLB model on one piece of
     *  text without saving anything, used to pre-fill the Bemba review
     *  fields in the upload flow before a teacher edits and saves. */
    public void translateText(String token, String text, TranslateCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("inputs", text);
            json.put("src_lang", "eng_Latn");
            json.put("tgt_lang", "bem_Latn");

            RequestBody requestBody = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(TRANSLATE_URL)
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
                    String responseBody = response.body() != null ? response.body().string() : "[]";
                    try {
                        if (response.isSuccessful()) {
                            JSONArray result = new JSONArray(responseBody);
                            String translated = result.length() > 0
                                    ? result.getJSONObject(0).optString("translation_text", "")
                                    : "";
                            callback.onSuccess(translated);
                        } else {
                            JSONObject error = new JSONObject(responseBody.startsWith("{") ? responseBody : "{}");
                            callback.onError(error.optString("detail", "Request failed (" + response.code() + ")"));
                        }
                    } catch (Exception e) {
                        callback.onError("Parsing error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Request error: " + e.getMessage());
        }
    }

    /** POST /api/lessons — teacher only. Saves a lesson exactly as given;
     *  the Bemba fields are expected to already be filled in (via
     *  translateText, then possibly edited by the teacher), not
     *  auto-translated server-side anymore. */
    public void createLesson(String token, String subject, String topicEn, String contentEn, String topicBem, String contentBem, LessonCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("subject", subject);
            json.put("topic_en", topicEn);
            json.put("content_en", contentEn);
            json.put("topic_bem", topicBem);
            json.put("content_bem", contentBem);

            RequestBody requestBody = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(LESSONS_URL)
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
            });
        } catch (Exception e) {
            callback.onError("Request error: " + e.getMessage());
        }
    }

    /** GET /api/teacher/lessons — teacher only. Every lesson they've posted, across all subjects. */
    public void teacherLessons(String token, LessonListCallback callback) {
        Request request = new Request.Builder()
                .url(TEACHER_LESSONS_URL)
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
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Request failed (" + response.code() + ")");
                    return;
                }
                try {
                    callback.onSuccess(new JSONArray(response.body().string()));
                } catch (Exception e) {
                    callback.onError("Parsing error: " + e.getMessage());
                }
            }
        });
    }

    /** POST /api/progress — fire-and-forget "viewed"/"listened" event. Pupils
     *  only; the server silently no-ops this for teachers previewing a lesson. */
    public void recordProgress(String token, String subject, String event, SimpleCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("subject", subject);
            json.put("event", event);

            RequestBody requestBody = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(PROGRESS_URL)
                    .post(requestBody)
                    .header("Authorization", "Bearer " + token)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (callback != null) callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (callback == null) return;
                    if (response.isSuccessful()) callback.onSuccess();
                    else callback.onError("Request failed (" + response.code() + ")");
                }
            });
        } catch (Exception e) {
            if (callback != null) callback.onError("Request error: " + e.getMessage());
        }
    }

    /** GET /api/progress — percentage (0/50/100) per subject for the logged-in pupil. */
    public void getProgress(String token, ProgressCallback callback) {
        Request request = new Request.Builder()
                .url(PROGRESS_URL)
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
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Request failed (" + response.code() + ")");
                    return;
                }
                try {
                    callback.onSuccess(new JSONArray(response.body().string()));
                } catch (Exception e) {
                    callback.onError("Parsing error: " + e.getMessage());
                }
            }
        });
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}