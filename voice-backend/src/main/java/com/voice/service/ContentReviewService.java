package com.voice.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.voice.model.User;
import com.voice.model.ViolationRecord;
import com.voice.repository.ViolationRecordRepository;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 内容审查服务：复用与对话相同的 DeepSeek 模型，检测涉黄/涉政等违规内容。
 * 命中违规时：记录到管理中心（violation_record），并返回违规原因供前端警告。
 */
@Service
public class ContentReviewService {

    private static final Logger log = LoggerFactory.getLogger(ContentReviewService.class);

    @Value("${api.deepseek.api-key}")
    private String apiKey;

    @Value("${api.deepseek.model:deepseek-v4-pro}")
    private String model;

    @Value("${admin.username:zipad}")
    private String adminUsername;

    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build();

    private final Gson gson = new Gson();

    @Autowired
    private ViolationRecordRepository violationRecordRepository;

    @Autowired
    private UserService userService;

    /** 审查结果 */
    public static class ReviewResult {
        public boolean pass;          // 是否通过
        public String reason;         // 违规原因（"涉黄"/"涉政"/"正常"）
        public String raw;            // 模型原始返回

        ReviewResult(boolean pass, String reason, String raw) {
            this.pass = pass;
            this.reason = reason;
            this.raw = raw;
        }
    }

    /**
     * 审查文本。仅当明确判定为涉黄/涉政时拦截，其余放行。
     */
    public ReviewResult review(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ReviewResult(true, "正常", "");
        }
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("stream", false);
            requestBody.addProperty("temperature", 0.0);

            JsonArrayOfMessages messages = new JsonArrayOfMessages();
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", "你是内容安全审查员。判断用户文本是否包含：1.涉黄（色情、淫秽、露骨性描写）2.涉政（政治敏感、颠覆国家政权、破坏社会稳定、攻击党政机关）。只输出一个JSON：{\"pass\":true或false,\"reason\":\"涉黄\"或\"涉政\"或\"正常\"}。涉黄或涉政时pass为false。不要输出其他内容。");
            messages.add(system);
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", text);
            messages.add(user);
            requestBody.add("messages", messages.asArray());

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    // 审查服务失败时放行（不阻断正常使用）
                    log.warn("内容审查服务失败 code={} body={}", response.code(), responseBody);
                    return new ReviewResult(true, "正常", responseBody);
                }
                JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                String content = json.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
                return parseResult(content);
            }
        } catch (Exception e) {
            log.warn("内容审查异常，放行: {}", e.getMessage());
            return new ReviewResult(true, "正常", "");
        }
    }

    private ReviewResult parseResult(String content) {
        String cleaned = content.trim();
        // 去掉可能的 ```json 包裹
        cleaned = cleaned.replaceAll("(?s)^```(json)?\\s*", "").replaceAll("(?s)\\s*```$", "").trim();
        try {
            JsonObject obj = gson.fromJson(cleaned, JsonObject.class);
            boolean pass = obj.has("pass") ? obj.get("pass").getAsBoolean() : true;
            String reason = obj.has("reason") ? obj.get("reason").getAsString() : "正常";
            return new ReviewResult(pass, reason, cleaned);
        } catch (Exception e) {
            // 无法解析模型返回时，兜底按关键词简单判断
            return fallbackReview(content);
        }
    }

    private ReviewResult fallbackReview(String content) {
        String c = content == null ? "" : content;
        boolean pass = c.contains("\"pass\":false") || c.contains("\"pass\": false");
        String reason = c.contains("涉政") ? "涉政" : (c.contains("涉黄") ? "涉黄" : "正常");
        return new ReviewResult(!pass, reason, content);
    }

    /**
     * 命中违规：记录到管理中心
     */
    public void recordViolation(Long userId, String source, String reason, String content) {
        try {
            ViolationRecord record = new ViolationRecord();
            record.setUserId(userId);
            String username = "未知用户";
            User user = userService.findById(userId);
            if (user != null) {
                username = user.getUsername();
            }
            record.setUsername(username);
            record.setSource(source);
            record.setViolationReason(reason == null || reason.isEmpty() ? "违规内容" : reason);
            String truncated = content == null ? "" : content;
            if (truncated.length() > 1900) {
                truncated = truncated.substring(0, 1900);
            }
            record.setContent(truncated);
            record.setStatus(0);
            violationRecordRepository.save(record);
        } catch (Exception e) {
            log.error("记录违规内容失败", e);
        }
    }

    /** 是否为管理员（数据库中 username 为 zipad 的账户） */
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userService.findById(userId);
        return user != null && adminUsername.equalsIgnoreCase(user.getUsername());
    }

    /** 辅助：简易 JSON 数组构建 */
    private static class JsonArrayOfMessages {
        private final com.google.gson.JsonArray array = new com.google.gson.JsonArray();

        void add(JsonObject obj) {
            array.add(obj);
        }

        com.google.gson.JsonArray asArray() {
            return array;
        }
    }
}
