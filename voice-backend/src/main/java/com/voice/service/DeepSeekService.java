package com.voice.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.voice.model.AiChatHistory;

@Service
public class DeepSeekService {
    
    @Value("${api.deepseek.api-key}")
    private String apiKey;

    @Value("${api.deepseek.model:deepseek-v4-pro}")
    private String defaultModel;
    
    @Value("${ai.prompt:You are a helpful AI assistant.}")
    private String systemPrompt;
    
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private OkHttpClient client;
    private Gson gson;
    
    public DeepSeekService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }
    
    public String chat(String userMessage, String userName) throws IOException {
        return chat(userMessage, userName, defaultModel, null, "", Collections.emptyList());
    }

    public String chat(String userMessage, String userName, String model,
                       String customPrompt, String memory, List<AiChatHistory> history) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false);
        requestBody.addProperty("temperature", 0.7);
        
        JsonArray messages = new JsonArray();
        
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        String finalPrompt = systemPrompt;
        if (customPrompt != null && !customPrompt.trim().isEmpty()) {
            finalPrompt += " " + customPrompt.trim();
        }
        if (userName != null && !userName.isEmpty()) {
            finalPrompt += " Current user is " + userName + ".";
        }
        if (memory != null && !memory.trim().isEmpty()) {
            finalPrompt += "\n以下是该用户的云端长期记忆，请自然使用，不要逐字复述：\n" + memory.trim();
        }
        systemMsg.addProperty("content", finalPrompt);
        messages.add(systemMsg);

        for (AiChatHistory item : history) {
            JsonObject historyMessage = new JsonObject();
            historyMessage.addProperty("role", item.getRole());
            historyMessage.addProperty("content", item.getContent());
            messages.add(historyMessage);
        }
        
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);
        
        requestBody.add("messages", messages);
        
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("AI 服务请求失败: " + response.code() + " " + responseBody);
            }
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            return json.getAsJsonArray("choices")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content")
                    .getAsString();
        }
    }

    public String summarizeMemory(String existingMemory, String userMessage,
                                  String assistantMessage) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", defaultModel);
        requestBody.addProperty("stream", false);
        requestBody.addProperty("temperature", 0.2);
        requestBody.addProperty("max_tokens", 400);

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", "你是记忆整理器。将旧记忆和本轮对话压缩为简洁中文事实，保留用户偏好、身份、重要经历、长期目标和未完成事项；删除寒暄、重复、临时细节和敏感凭据。只输出记忆正文，不超过1200字。");
        messages.add(system);
        JsonObject input = new JsonObject();
        input.addProperty("role", "user");
        input.addProperty("content", "旧记忆：\n" + (existingMemory == null ? "" : existingMemory)
                + "\n\n本轮用户：\n" + userMessage
                + "\n\n本轮助手：\n" + assistantMessage);
        messages.add(input);
        requestBody.add("messages", messages);

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("记忆精简失败: " + response.code() + " " + responseBody);
            }
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            return json.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        }
    }
}