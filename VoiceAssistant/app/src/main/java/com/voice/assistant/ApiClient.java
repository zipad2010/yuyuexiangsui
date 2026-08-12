package com.voice.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import okhttp3.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    private static final String BASE_URL = BuildConfig.API_BASE_URL;
    
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

    public static String resolveResourceUrl(String resourceUrl) {
        if (resourceUrl == null || resourceUrl.trim().isEmpty()) {
            return resourceUrl;
        }
        HttpUrl resolvedUrl = HttpUrl.parse(BASE_URL).resolve(resourceUrl);
        return resolvedUrl == null ? resourceUrl : resolvedUrl.toString();
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
        return voiceChat(audioData, token, model, enableThinking, customPrompt, 0);
    }

    public String voiceChat(byte[] audioData, String token, String model,
                            boolean enableThinking, String customPrompt, long conversationId) throws IOException {
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
        if (conversationId > 0) {
            bodyBuilder.addFormDataPart("conversationId", String.valueOf(conversationId));
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
    
    /**
     * 实时通话：发送一段语音（PCM 16kHz），后端做 ASR → AI → MiMo TTS，
     * 返回识别文本、AI 回复及合成语音（base64 wav）。
     */
    public String voiceCall(byte[] audioData, String token) throws IOException {
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", "audio.pcm",
                    RequestBody.create(audioData, MediaType.parse("audio/pcm")));

        Request request = new Request.Builder()
                .url(BASE_URL + "/voice/call")
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

    public String getChatHistory(String token) throws IOException {
        return getChatHistory(token, 0);
    }

    public String getChatHistory(String token, long conversationId) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/voice/chat/history").newBuilder();
        if (conversationId > 0) {
            urlBuilder.addQueryParameter("conversationId", String.valueOf(conversationId));
        }
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    /**
     * 撤回一条历史消息（后端会同时删除对应的 AI 回复）
     */
    public String deleteChatHistory(long historyId, String token) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("historyId", historyId);
        Request request = new Request.Builder()
                .url(BASE_URL + "/voice/chat/history/delete")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(body.toString(), JSON))
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
        return textChat(message, token, model, enableThinking, customPrompt, 0);
    }
    
    public String textChat(String message, String token, String model,
                           boolean enableThinking, String customPrompt, long conversationId) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("message", message);
        if (model != null && !model.isEmpty()) {
            body.put("model", model);
        }
        body.put("enableThinking", enableThinking);
        if (customPrompt != null && !customPrompt.isEmpty()) {
            body.put("customPrompt", customPrompt);
        }
        if (conversationId > 0) {
            body.put("conversationId", conversationId);
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

    public String uploadAvatar(byte[] imageData, String fileName, String mimeType, String token) throws IOException {
        RequestBody imageBody = RequestBody.create(imageData, MediaType.parse(mimeType));
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("avatar", fileName, imageBody)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/user/avatar")
                .addHeader("Authorization", "Bearer " + token)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String uploadWallpaper(byte[] imageData, String fileName, String mimeType, String token) throws IOException {
        RequestBody imageBody = RequestBody.create(imageData, MediaType.parse(mimeType));
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("wallpaper", fileName, imageBody)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/user/wallpaper")
                .addHeader("Authorization", "Bearer " + token)
                .post(body)
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
    
    public String getConversation(long targetUserId, String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/messages/conversation/" + targetUserId)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String findMessageRecipient(String username, String token) throws IOException {
        HttpUrl url = HttpUrl.parse(BASE_URL + "/messages/recipient").newBuilder()
                .addQueryParameter("username", username)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    /**
     * 发起私信时的用户列表搜索：keyword 为空返回全部用户
     */
    public String searchMessageRecipients(String keyword, String token) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/messages/recipients").newBuilder();
        if (keyword != null && !keyword.trim().isEmpty()) {
            urlBuilder.addQueryParameter("keyword", keyword.trim());
        }
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String sendMessage(long toUserId, String content, String token) throws IOException, JSONException {
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

    public String getNotificationSummary(long forumCheckedAt, String token) throws IOException {
        HttpUrl url = HttpUrl.parse(BASE_URL + "/messages/notifications/summary").newBuilder()
                .addQueryParameter("forumCheckedAt", String.valueOf(forumCheckedAt))
                .build();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String markMessagesRead(String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/messages/mark-read")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(new byte[0], JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    // ========== 管理中心（仅管理员） ==========

    /**
     * 当前用户是否为管理员（后端按 username=zipad 判定）
     */
    public String isAdmin(String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/moderation/is-admin")
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    /**
     * 违规记录列表（仅管理员）
     */
    public String getViolations(int status, String token) throws IOException {
        HttpUrl url = HttpUrl.parse(BASE_URL + "/moderation/violations").newBuilder()
                .addQueryParameter("status", String.valueOf(status))
                .build();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    /**
     * 处理违规记录（标记已处理）
     */
    public String resolveViolation(long recordId, String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/moderation/violations/" + recordId + "/handle")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(new byte[0], JSON))
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
        String selectedModel = prefs.getString("selected_model", "deepseek-v4-pro");
        if ("deepseek-chat".equals(selectedModel) || "deepseek-reasoner".equals(selectedModel)) {
            selectedModel = "deepseek-v4-pro";
            prefs.edit()
                    .putString("selected_model", selectedModel)
                    .putBoolean("enable_thinking", false)
                    .apply();
        }
        return selectedModel;
    }
    
    /**
     * 获取深度思考开关状态
     */
    public boolean isEnableThinking() {
        SharedPreferences prefs = context.getSharedPreferences("voice_prefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("enable_thinking", false);
    }

    // ========== 对话会话 ==========

    public String getChatConversations(String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/conversations/list")
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String createConversation(String title, Long personaId, String token) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("title", title == null ? "" : title);
        if (personaId != null && personaId > 0) {
            body.put("personaId", personaId);
        }
        Request request = new Request.Builder()
                .url(BASE_URL + "/conversations/create")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String renameConversation(long conversationId, String title, String token) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("title", title == null ? "" : title);
        Request request = new Request.Builder()
                .url(BASE_URL + "/conversations/" + conversationId + "/rename")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String setConversationPersona(long conversationId, Long personaId, String token) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        if (personaId != null && personaId > 0) {
            body.put("personaId", personaId);
        } else {
            body.put("personaId", 0);
        }
        Request request = new Request.Builder()
                .url(BASE_URL + "/conversations/" + conversationId + "/persona")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String deleteConversation(long conversationId, String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/conversations/" + conversationId + "/delete")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(new byte[0], JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    // ========== 人设投稿中心 ==========

    public String getPersonaList(String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/personas/list")
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String getSubscribedPersonas(String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/personas/subscribed")
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String getMyPersonas(String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/personas/mine")
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String publishPersona(String name, String description, String prompt, String token) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("name", name == null ? "" : name);
        body.put("description", description == null ? "" : description);
        body.put("prompt", prompt == null ? "" : prompt);
        Request request = new Request.Builder()
                .url(BASE_URL + "/personas/publish")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String subscribePersona(long personaId, String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/personas/" + personaId + "/subscribe")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(new byte[0], JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String unsubscribePersona(long personaId, String token) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/personas/" + personaId + "/unsubscribe")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(new byte[0], JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    // ========== 论坛媒体上传 ==========

    public String uploadForumMedia(byte[] mediaData, String fileName, String mimeType, String token) throws IOException {
        RequestBody mediaBody = RequestBody.create(mediaData, MediaType.parse(mimeType));
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("media", fileName, mediaBody)
                .build();
        Request request = new Request.Builder()
                .url(BASE_URL + "/forum/upload")
                .addHeader("Authorization", "Bearer " + token)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String createForumPostWithMedia(String title, String content, List<String> mediaUrls, String token) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("title", title);
        body.put("content", content);
        if (mediaUrls != null && !mediaUrls.isEmpty()) {
            body.put("mediaUrls", new org.json.JSONArray(mediaUrls));
        }
        Request request = new Request.Builder()
                .url(BASE_URL + "/forum/post")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
}