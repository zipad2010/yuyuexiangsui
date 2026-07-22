package com.voice.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import okhttp3.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    // 你的服务器IP
    private static final String BASE_URL = "http://45.207.199.36:8080/api";
    
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private OkHttpClient client;
    private Context context;
    
    public ApiClient(Context context) {
        this.context = context;
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    // ========== 认证相关 ==========
    
    public String login(String username, String password) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", password);
        
        Request request = new Request.Builder()
                .url(BASE_URL + "/auth/login")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    public String register(String username, String password) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", password);
        
        Request request = new Request.Builder()
                .url(BASE_URL + "/auth/register")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    // ========== 语音对话 ==========
    
    public String voiceChat(byte[] audioData, String token) throws IOException {
        return voiceChat(audioData, token, null, false, null);
    }
    
    public String voiceChat(byte[] audioData, String token, String model, 
                            boolean enableThinking, String customPrompt) throws IOException {
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", "audio.pcm",
                    RequestBody.create(audioData, MediaType.parse("audio/pcm")));
        
        // 添加模型参数（赞助者专用）
        if (model != null && !model.isEmpty()) {
            bodyBuilder.addFormDataPart("model", model);
        }
        bodyBuilder.addFormDataPart("enableThinking", String.valueOf(enableThinking));
        if (customPrompt != null && !customPrompt.isEmpty()) {
            bodyBuilder.addFormDataPart("customPrompt", customPrompt);
        }
        
        Request request = new Request.Builder()
                .url(BASE_URL + "/voice/chat")
                .addHeader("Authorization", "Bearer " + token)
                .post(bodyBuilder.build())
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    public String getBalance(String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/voice/balance")
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    public String textChat(String message, String token) throws IOException, JSONException {
        return textChat(message, token, null, false, null);
    }
    
    public String textChat(String message, String token, String model,
                           boolean enableThinking, String customPrompt) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("message", message);
        if (model != null && !model.isEmpty()) {
            body.put("model", model);
        }
        body.put("enableThinking", enableThinking);
        if (customPrompt != null && !customPrompt.isEmpty()) {
            body.put("customPrompt", customPrompt);
        }
        
        Request request = new Request.Builder()
                .url(BASE_URL + "/voice/chat/text")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    // ========== 个人中心 ==========
    
    public String getProfile(String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/user/profile")
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    public String updateProfile(String nickname, String signature, String token) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("nickname", nickname);
        body.put("signature", signature);
        
        Request request = new Request.Builder()
                .url(BASE_URL + "/user/profile")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    // ========== 论坛 ==========
    
    public String getForumPosts(String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/forum/posts")
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    public String getForumPost(Long postId, String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/forum/post/" + postId)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    public String createForumPost(String title, String content, String token) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("title", title);
        body.put("content", content);
        
        Request request = new Request.Builder()
                .url(BASE_URL + "/forum/post")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    public String createForumReply(Long postId, String content, String token) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("postId", postId);
        body.put("content", content);
        
        Request request = new Request.Builder()
                .url(BASE_URL + "/forum/reply")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    // ========== 私信 ==========
    
    public String getConversations(String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/messages/conversations")
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    public String getConversation(Long targetUserId, String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/messages/conversation/" + targetUserId)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    public String sendMessage(Long toUserId, String content, String token) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("toUserId", toUserId);
        body.put("content", content);
        
        Request request = new Request.Builder()
                .url(BASE_URL + "/messages/send")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    // ========== 模型切换（赞助者专用） ==========
    
    /**
     * 获取用户信息（包含赞助者状态）
     */
    public String getUserInfo(String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/voice/user/info")
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    /**
     * 获取可用模型列表
     */
    public String getAvailableModels(String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/models/list")
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
    
    // ========== 本地存储模型选择 ==========
    
    /**
     * 保存用户选择的模型
     */
    public void saveSelectedModel(String model, boolean enableThinking) {
        SharedPreferences prefs = context.getSharedPreferences("voice_prefs", Context.MODE_PRIVATE);
        prefs.edit()
            .putString("selected_model", model)
            .putBoolean("enable_thinking", enableThinking)
            .apply();
    }
    
    /**
     * 获取用户选择的模型
     */
    public String getSelectedModel() {
        SharedPreferences prefs = context.getSharedPreferences("voice_prefs", Context.MODE_PRIVATE);
        return prefs.getString("selected_model", "deepseek-chat");
    }
    
    /**
     * 获取深度思考开关状态
     */
    public boolean isEnableThinking() {
        SharedPreferences prefs = context.getSharedPreferences("voice_prefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("enable_thinking", false);
    }
}