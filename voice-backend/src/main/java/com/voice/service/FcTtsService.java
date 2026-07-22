package com.voice.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class FcTtsService {

    private static final Logger log = LoggerFactory.getLogger(FcTtsService.class);

    @Value("${fc.gpt-sovits.url}")
    private String fcUrl;

    private final OkHttpClient client;
    private final Gson gson;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public FcTtsService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    // 兼容入口（只传文本和参考音频路径，自动使用默认情感）
    public String synthesizeText(String text, String refAudioPath) throws IOException {
        return synthesize(text, "开心_happy");
    }

    // 核心合成方法（指定文本和情感）
    public String synthesize(String text, String emotion) throws IOException {
        try {
            String audioUrl = requestSynthesis(text, emotion);
            if (audioUrl == null || audioUrl.isEmpty()) {
                log.error("GSVI 返回的 audio_url 为空");
                return null;
            }

            log.info("GSVI 合成成功，音频 URL: {}", audioUrl);

            byte[] audioData = downloadAudio(audioUrl);
            if (audioData == null || audioData.length == 0) {
                log.error("下载音频失败");
                return null;
            }

            log.info("音频下载成功，大小: {} bytes", audioData.length);

            return Base64.getEncoder().encodeToString(audioData);

        } catch (Exception e) {
            log.error("语音合成失败", e);
            return null;
        }
    }

    private String requestSynthesis(String text, String emotion) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("version", "v4");
        body.addProperty("model_name", "GPT-SoVITS-v4-大月下AI模型");
        body.addProperty("prompt_text_lang", "中文");
        body.addProperty("emotion", emotion);
        body.addProperty("text", text);
        body.addProperty("text_lang", "中文");
        body.addProperty("media_type", "wav");
        body.addProperty("speed_facter", 1.0);
        body.addProperty("temperature", 1.0);

        String url = fcUrl + "/infer_single";
        log.info("请求 GSVI: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            log.info("GSVI 响应: {}", responseBody);

            if (!response.isSuccessful()) {
                log.error("GSVI 请求失败: {}", response.code());
                return null;
            }

            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            String msg = json.get("msg").getAsString();

            if ("合成成功".equals(msg) && json.has("audio_url")) {
                String audioUrl = json.get("audio_url").getAsString();
                if (audioUrl.startsWith("http://0.0.0.0:")) {
                    audioUrl = audioUrl.replace("http://0.0.0.0:", "http://127.0.0.1:");
                }
                return audioUrl;
            }

            log.error("GSVI 合成失败: {}", msg);
            return null;
        }
    }

    private byte[] downloadAudio(String audioUrl) throws IOException {
        log.info("下载音频: {}", audioUrl);

        Request request = new Request.Builder()
                .url(audioUrl)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("下载音频失败: {}", response.code());
                return null;
            }
            return response.body().bytes();
        }
    }
}