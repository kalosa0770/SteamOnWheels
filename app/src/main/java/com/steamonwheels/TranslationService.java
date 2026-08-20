package com.steamonwheels;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class TranslationService {

    private static final String API_URL = "https://friday-exposure-womankind.ngrok-free.dev/translate";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public interface TranslationCallback {
        void onSuccess(String translatedBembaText);
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
                    .url(API_URL)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network connection error: " + e.getMessage());
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
                                callback.onError("Empty translation response from server.");
                            }
                        } catch (Exception e) {
                            callback.onError("Failed to parse AI response: " + e.getMessage());
                        }
                    } else {
                        callback.onError("Server returned error: HTTP " + response.code());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Payload creation error: " + e.getMessage());
        }
    }
}