package com.voice.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 小米 MiMo 实时语音合成服务。
 * 使用 mimo-v2.5-tts-voicedesign 模型进行语音复刻，
 * 参考音频路径可通过配置 mimo.tts.reference-audio-path 指定（默认 /www/1.wav）。
 */
@Service
public class MiMoTtsService {

    private static final Logger log = LoggerFactory.getLogger(MiMoTtsService.class);

    @Value("${mimo.tts.url:https://api.xiaomimimo.com/v1/chat/completions}")
    private String ttsUrl;

    @Value("${mimo.tts.api-key}")
    private String apiKey;

    @Value("${mimo.tts.model:mimo-v2.5-tts-voicedesign}")
    private String model;

    @Value("${mimo.tts.reference-audio-path:/www/1.wav}")
    private String referenceAudioPath;

    /** 语音复刻参考音频（base64），启动时加载并缓存 */
    private String referenceAudioBase64;

    private final OkHttpClient client;
    private final Gson gson;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public MiMoTtsService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    @PostConstruct
    public void loadReferenceAudio() {
        try {
            byte[] data = Files.readAllBytes(Paths.get(referenceAudioPath));
            if (data.length == 0) {
                log.error("语音复刻参考音频为空: {}", referenceAudioPath);
                referenceAudioBase64 = null;
                return;
            }
            referenceAudioBase64 = Base64.getEncoder().encodeToString(data);
            log.info("已加载语音复刻参考音频: {} ({} bytes)", referenceAudioPath, data.length);
        } catch (Exception e) {
            log.error("加载语音复刻参考音频失败: {}", referenceAudioPath, e);
            referenceAudioBase64 = null;
        }
    }

    /**
     * 将文本合成为语音，返回 wav 音频的 base64 字符串。
     *
     * @param text 需要合成的文本
     * @return base64 编码的 wav 音频；合成失败抛出 IOException
     */
    public String synthesize(String text) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        if (referenceAudioBase64 == null) {
            throw new IOException("语音复刻参考音频不可用: " + referenceAudioPath);
        }
        // 去除括号及括号内的旁白/提示内容（如“（笑）”“（轻声）”“——呵呵”），避免被语音合成读出来
        String cleanText = stripParentheses(text.trim());
        if (cleanText.isEmpty()) {
            log.warn("文本去除括号后为空，原文本: {}", text);
            return null;
        }
        String audioBase64 = requestSynthesis(cleanText);
        if (audioBase64 == null || audioBase64.isEmpty()) {
            throw new IOException("MiMo TTS 返回音频为空");
        }
        log.info("MiMo TTS 合成成功，文本长度: {}，音频 {} 字符", cleanText.length(), audioBase64.length());
        return audioBase64;
    }

    /**
     * 移除文本中成对的括号及其内部内容（支持中文全角（）与英文半角()），
     * 并清理遗留的空白。AI 回复中常包含（笑）、(低声) 等旁白，不应被语音合成朗读。
     */
    String stripParentheses(String text) {
        if (text == null) {
            return null;
        }
        String result = text;
        // 循环删除最内层成对括号，直到没有可匹配的括号对
        String prev;
        do {
            prev = result;
            result = result.replaceAll("[（(][^（()）]*[）)]", "");
        } while (!result.equals(prev));
        // 清理多余空白与可能的残留修饰符（如——、*）
        result = result.replaceAll("[\\s\\u3000]+", " ").trim();
        result = result.replaceAll("^[—–*]+\\s*|[—–*]+\\s*$", "").trim();
        return result;
    }

    private String requestSynthesis(String text) throws IOException {
        // 语音复刻：通过 audio.input_audio 传入参考音频
        JsonObject inputAudio = new JsonObject();
        inputAudio.addProperty("data", referenceAudioBase64);
        inputAudio.addProperty("format", "wav");

        JsonObject audio = new JsonObject();
        audio.addProperty("voice", "voice-design");
        audio.addProperty("format", "wav");
        audio.add("input_audio", inputAudio);

        JsonArray modalities = new JsonArray();
        modalities.add("text");
        modalities.add("audio");

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", text);
        messages.add(userMsg);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("modalities", modalities);
        body.add("audio", audio);
        body.add("messages", messages);

        Request request = new Request.Builder()
                .url(ttsUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        log.info("请求 MiMo TTS: {} model={}", ttsUrl, model);

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                log.error("MiMo TTS 请求失败: {} {}", response.code(), responseBody);
                throw new IOException("MiMo TTS 请求失败: HTTP " + response.code());
            }

            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            if (json == null || !json.has("choices")
                    || json.getAsJsonArray("choices").size() == 0) {
                log.error("MiMo TTS 响应缺少 choices: {}", responseBody);
                throw new IOException("MiMo TTS 响应异常");
            }

            JsonObject message = json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message");
            if (message == null || !message.has("audio")) {
                log.error("MiMo TTS 响应缺少音频: {}", responseBody);
                throw new IOException("MiMo TTS 响应缺少音频数据");
            }

            JsonObject audioOut = message.getAsJsonObject("audio");
            if (audioOut == null || !audioOut.has("data")) {
                throw new IOException("MiMo TTS 响应缺少音频数据字段");
            }
            return audioOut.get("data").getAsString();
        }
    }
}
