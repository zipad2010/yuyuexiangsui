package com.voice.controller;

import com.voice.model.ApiResponse;
import com.voice.model.User;
import com.voice.service.*;
import com.voice.config.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/voice")
@CrossOrigin(origins = "*")
public class VoiceController {

    private static final Logger log = LoggerFactory.getLogger(VoiceController.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private BaiduAsrService baiduAsrService;

    @Autowired
    private DeepSeekService deepSeekService;

    @Autowired
    private FcTtsService fcTtsService;

    @Autowired
    private MiMoTtsService miMoTtsService;

    @Autowired
    private AiChatHistoryService aiChatHistoryService;

    @Autowired
    private AiMemoryService aiMemoryService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private ContentReviewService contentReviewService;

    @Value("${ai.history.enabled:true}")
    private boolean historyEnabled;

    @Value("${ai.history.max-messages:20}")
    private int historyMaxMessages;

    @Value("${api.deepseek.model:deepseek-v4-pro}")
    private String chatModel;

    private String selectedModel(User user, String requestedModel, boolean enableThinking) {
        return chatModel;
    }

    private Long getUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }

    private Long resolveConversationId(Long userId, Object conversationIdObj) {
        if (conversationIdObj == null) {
            return null;
        }
        Long conversationId;
        try {
            conversationId = Long.parseLong(conversationIdObj.toString());
        } catch (RuntimeException e) {
            return null;
        }
        if (conversationId <= 0) {
            return null;
        }
        com.voice.model.Conversation conversation =
                conversationService.getOwnedConversation(userId, conversationId);
        return conversation == null ? null : conversationId;
    }

    private String resolvePersonaPrompt(Long userId, Long conversationId) {
        if (conversationId == null || conversationId <= 0) {
            return null;
        }
        com.voice.model.Conversation conversation =
                conversationService.getOwnedConversation(userId, conversationId);
        if (conversation == null || conversation.getActivePersonaId() == null) {
            return null;
        }
        return personaService.getSubscribedPrompt(userId, conversation.getActivePersonaId());
    }

    private List<com.voice.model.AiChatHistory> getConversationContext(Long userId, Long conversationId) {
        return aiChatHistoryService.getConversationHistory(userId, conversationId, historyMaxMessages);
    }

    @PostMapping("/chat")
    public ApiResponse<VoiceChatResponse> chat(
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(value = "model", required = false) String requestedModel,
            @RequestParam(value = "enableThinking", defaultValue = "false") boolean enableThinking,
            @RequestParam(value = "customPrompt", required = false) String customPrompt,
            @RequestParam(value = "conversationId", required = false) Long conversationId,
            HttpServletRequest request) {

        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }

        User user = userService.findById(userId);
        if (user == null || user.getStatus() != 1) {
            return ApiResponse.error(403, "用户状态异常");
        }

        boolean pointDeducted = false;
        try {
            byte[] audioData = audioFile.getBytes();
            String recognizedText = baiduAsrService.recognize(audioData);

            if (recognizedText == null || recognizedText.isEmpty()) {
                return ApiResponse.error(400, "语音识别失败，请重试");
            }

            // 内容审查：违规时拦截、退款并记录
            ContentReviewService.ReviewResult review = contentReviewService.review(recognizedText);
            if (!review.pass) {
                if (pointDeducted) {
                    balanceService.refundPoint(userId, "语音对话违规退款");
                }
                contentReviewService.recordViolation(userId, "chat", review.reason, recognizedText);
                return ApiResponse.error(403, "内容不符合规范（" + review.reason + "），已警告并记录");
            }

            if (!balanceService.deductPoint(userId, "conversation", recognizedText)) {
                return ApiResponse.error(402, "积分不足，请充值");
            }
            pointDeducted = true;

            Long convId = resolveConversationId(userId, conversationId);
            String prompt = customPrompt;
            if ((prompt == null || prompt.trim().isEmpty()) && convId != null) {
                prompt = resolvePersonaPrompt(userId, convId);
            }
            String memory = aiMemoryService.getSummary(userId);
            String model = selectedModel(user, requestedModel, enableThinking);
            String aiReply = deepSeekService.chat(recognizedText, user.getUsername(), model,
                    prompt, memory, getConversationContext(userId, convId));
            if (historyEnabled) {
                aiChatHistoryService.saveTurn(userId, convId, recognizedText, aiReply);
                if (convId != null) {
                    conversationService.touchConversation(convId);
                }
            }
            updateMemory(userId, memory, recognizedText, aiReply);

            String audioBase64 = fcTtsService.synthesizeText(aiReply, null);

            VoiceChatResponse response = new VoiceChatResponse();
            response.setRecognizedText(recognizedText);
            response.setAiReply(aiReply);
            response.setAudioBase64(audioBase64);
            response.setBalance(new BigDecimal(userService.getPoints(userId)));

            return ApiResponse.success(response);

        } catch (Exception e) {
            if (pointDeducted) {
                balanceService.refundPoint(userId, "语音对话处理失败退款");
            }
            log.error("语音对话失败", e);
            return ApiResponse.error(500, "处理失败: " + e.getMessage());
        }
    }

    @PostMapping("/call")
    public ApiResponse<VoiceChatResponse> call(
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(value = "conversationId", required = false) Long conversationId,
            HttpServletRequest request) {

        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }

        User user = userService.findById(userId);
        if (user == null || user.getStatus() != 1) {
            return ApiResponse.error(403, "用户状态异常");
        }

        boolean pointDeducted = false;
        try {
            byte[] audioData = audioFile.getBytes();
            String recognizedText = baiduAsrService.recognize(audioData);

            if (recognizedText == null || recognizedText.isEmpty()) {
                return ApiResponse.error(400, "语音识别失败，请重试");
            }

            // 内容审查：违规时拦截、退款并记录
            ContentReviewService.ReviewResult review = contentReviewService.review(recognizedText);
            if (!review.pass) {
                if (pointDeducted) {
                    balanceService.refundPoint(userId, "实时通话违规退款");
                }
                contentReviewService.recordViolation(userId, "chat", review.reason, recognizedText);
                return ApiResponse.error(403, "内容不符合规范（" + review.reason + "），已警告并记录");
            }

            if (!balanceService.deductPoint(userId, "conversation", recognizedText)) {
                return ApiResponse.error(402, "积分不足，请充值");
            }
            pointDeducted = true;

            Long convId = resolveConversationId(userId, conversationId);
            String prompt = resolvePersonaPrompt(userId, convId);
            String memory = aiMemoryService.getSummary(userId);
            String model = selectedModel(user, null, false);
            String aiReply = deepSeekService.chat(recognizedText, user.getUsername(), model,
                    prompt, memory, getConversationContext(userId, convId));
            if (historyEnabled) {
                aiChatHistoryService.saveTurn(userId, convId, recognizedText, aiReply);
                if (convId != null) {
                    conversationService.touchConversation(convId);
                }
            }
            updateMemory(userId, memory, recognizedText, aiReply);

            String audioBase64 = miMoTtsService.synthesize(aiReply);

            VoiceChatResponse response = new VoiceChatResponse();
            response.setRecognizedText(recognizedText);
            response.setAiReply(aiReply);
            response.setAudioBase64(audioBase64);
            response.setBalance(new BigDecimal(userService.getPoints(userId)));

            return ApiResponse.success(response);

        } catch (Exception e) {
            if (pointDeducted) {
                balanceService.refundPoint(userId, "实时通话处理失败退款");
            }
            log.error("实时通话失败", e);
            return ApiResponse.error(500, "处理失败: " + e.getMessage());
        }
    }

    @GetMapping("/balance")
    public ApiResponse<Integer> getBalance(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(userService.getPoints(userId));
    }

    @PostMapping("/chat/text")
    public ApiResponse<Map<String, String>> textChat(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {

        Long userId = getUserIdFromToken(httpRequest);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }

        String message = request.get("message") == null ? null : request.get("message").toString();
        if (message == null || message.isEmpty()) {
            return ApiResponse.error(400, "消息不能为空");
        }

        boolean pointDeducted = false;
        try {
            User user = userService.findById(userId);
            if (user == null || user.getStatus() != 1) {
                return ApiResponse.error(403, "用户状态异常");
            }

            if (!balanceService.deductPoint(userId, "conversation", message)) {
                return ApiResponse.error(402, "积分不足，请充值");
            }
            pointDeducted = true;

            // 内容审查：违规时拦截、退款并记录
            ContentReviewService.ReviewResult review = contentReviewService.review(message);
            if (!review.pass) {
                if (pointDeducted) {
                    balanceService.refundPoint(userId, "文字对话违规退款");
                }
                contentReviewService.recordViolation(userId, "chat", review.reason, message);
                return ApiResponse.error(403, "内容不符合规范（" + review.reason + "），已警告并记录");
            }

            String requestedModel = request.get("model") == null ? null : request.get("model").toString();
            boolean enableThinking = Boolean.parseBoolean(String.valueOf(request.get("enableThinking")));
            String customPrompt = request.get("customPrompt") == null ? null : request.get("customPrompt").toString();
            Long convId = resolveConversationId(userId, request.get("conversationId"));
            String prompt = customPrompt;
            if ((prompt == null || prompt.trim().isEmpty()) && convId != null) {
                prompt = resolvePersonaPrompt(userId, convId);
            }
            String memory = aiMemoryService.getSummary(userId);
            String model = selectedModel(user, requestedModel, enableThinking);
            String aiReply = deepSeekService.chat(message, user.getUsername(), model,
                    prompt, memory, getConversationContext(userId, convId));
            if (historyEnabled) {
                aiChatHistoryService.saveTurn(userId, convId, message, aiReply);
                if (convId != null) {
                    conversationService.touchConversation(convId);
                }
            }
            updateMemory(userId, memory, message, aiReply);

            String audioBase64 = fcTtsService.synthesizeText(aiReply, null);

            Map<String, String> result = new HashMap<>();
            result.put("aiReply", aiReply);
            result.put("audioBase64", audioBase64);
            result.put("balance", String.valueOf(userService.getPoints(userId)));

            return ApiResponse.success(result);

        } catch (Exception e) {
            if (pointDeducted) {
                balanceService.refundPoint(userId, "文字对话处理失败退款");
            }
            log.error("文字对话失败", e);
            return ApiResponse.error(500, "处理失败: " + e.getMessage());
        }
    }

    private void updateMemory(Long userId, String currentMemory,
                              String userMessage, String assistantMessage) {
        try {
            String summary = deepSeekService.summarizeMemory(
                    currentMemory, userMessage, assistantMessage);
            aiMemoryService.saveSummary(userId, summary);
        } catch (Exception memoryError) {
            log.warn("云端记忆精简失败，使用本地压缩", memoryError);
            aiMemoryService.rememberTurn(userId, userMessage, assistantMessage);
        }
    }

    @GetMapping("/chat/history")
    public ApiResponse<List<Map<String, Object>>> getChatHistory(
            @RequestParam(required = false) Long conversationId,
            HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        Long convId = resolveConversationId(userId, conversationId);
        return ApiResponse.success(aiChatHistoryService.getConversationViews(userId, convId));
    }

    /**
     * 撤回消息：删除指定历史记录（及对应的 AI 回复），撤回后不再作为上下文
     */
    @PostMapping("/chat/history/delete")
    public ApiResponse<String> deleteChatHistory(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        Long userId = getUserIdFromToken(httpRequest);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        Object historyIdObj = request.get("historyId");
        if (historyIdObj == null) {
            return ApiResponse.error(400, "缺少历史记录 ID");
        }
        long historyId;
        try {
            historyId = Long.parseLong(historyIdObj.toString());
        } catch (RuntimeException e) {
            return ApiResponse.error(400, "历史记录 ID 无效");
        }
        if (aiChatHistoryService.deleteMessage(userId, historyId)) {
            return ApiResponse.success("已撤回");
        }
        return ApiResponse.error(404, "消息不存在或已撤回");
    }

    @GetMapping("/user/info")
    public ApiResponse<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }

        User user = userService.findById(userId);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        int points = userService.getPoints(userId);
        result.put("points", points);
        result.put("balance", points);
        result.put("isSponsor", Integer.valueOf(1).equals(user.getIsSponsor()));

        return ApiResponse.success(result);
    }

    static class VoiceChatResponse {
        private String recognizedText;
        private String aiReply;
        private String audioBase64;
        private BigDecimal balance;

        public String getRecognizedText() { return recognizedText; }
        public void setRecognizedText(String recognizedText) { this.recognizedText = recognizedText; }
        public String getAiReply() { return aiReply; }
        public void setAiReply(String aiReply) { this.aiReply = aiReply; }
        public String getAudioBase64() { return audioBase64; }
        public void setAudioBase64(String audioBase64) { this.audioBase64 = audioBase64; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
    }
}
