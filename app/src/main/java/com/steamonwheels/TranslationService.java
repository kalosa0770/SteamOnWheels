package com.steamonwheels;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class TranslationService {

    private static final String TRANSLATE_URL = "https://steamonwheels-production.up.railway.app/translate";
    private static final String TTS_URL = "https://steamonwheels-production.up.railway.app/api/tts";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    public interface TranslationCallback {
        void onSuccess(String translatedBembaText);
        void onError(String errorMessage);
    }

    public interface AudioCallback {
        void onSuccess(byte[] audioBytes);
        void onError(String errorMessage);
    }

    public void translateToBemba(String englishText, TranslationCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("inputs", englishText);
            json.put("src_lang", "eng_Latn");
            json.put("tgt_lang", "bem_Latn");

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(TRANSLATE_URL)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String responseData = response.body().string();
                            JSONArray resArray = new JSONArray(responseData);
                            if (resArray.length() > 0) {
                                JSONObject item = resArray.getJSONObject(0);
                                String translatedText = item.getString("translation_text");
                                callback.onSuccess(translatedText);
                            } else {
                                callback.onError("Empty translation response.");
                            }
                        } catch (Exception e) {
                            callback.onError("Parsing error: " + e.getMessage());
                        }
                    } else {
                        callback.onError("API Error: HTTP " + response.code());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Request error: " + e.getMessage());
        }
    }

    // New TTS Audio API Method
    public void fetchAudio(String text, String langCode, AudioCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("text", text);
            json.put("lang", langCode); // "bem" or "eng"

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(TTS_URL)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        byte[] audioBytes = response.body().bytes();
                        callback.onSuccess(audioBytes);
                    } else {
                        callback.onError("TTS API Error: HTTP " + response.code());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Request error: " + e.getMessage());
        }
    }
}