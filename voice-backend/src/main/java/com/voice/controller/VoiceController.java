package com.voice.controller;

import com.voice.model.ApiResponse;
import com.voice.model.User;
import com.voice.service.*;
import com.voice.config.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.HashMap;
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

    private Long getUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }

    private int estimateDuration(byte[] audioData) {
        return audioData.length / 32000;
    }

    @PostMapping("/chat")
    public ApiResponse<VoiceChatResponse> chat(
            @RequestParam("audio") MultipartFile audioFile,
            HttpServletRequest request) {

        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }

        User user = userService.findById(userId);
        if (user == null || user.getStatus() != 1) {
            return ApiResponse.error(403, "用户状态异常");
        }

        try {
            byte[] audioData = audioFile.getBytes();
            String recognizedText = baiduAsrService.recognize(audioData);

            if (recognizedText == null || recognizedText.isEmpty()) {
                return ApiResponse.error(400, "语音识别失败，请重试");
            }

            int duration = estimateDuration(audioData);
            BigDecimal asrCost = new BigDecimal("0.0001").multiply(new BigDecimal(duration));
            if (!balanceService.deduct(userId, asrCost, "asr", recognizedText)) {
                return ApiResponse.error(402, "余额不足，请充值");
            }

            String aiReply = deepSeekService.chat(recognizedText, user.getUsername());

            BigDecimal aiCost = new BigDecimal("0.002");
            if (!balanceService.deduct(userId, aiCost, "ai", aiReply)) {
                return ApiResponse.error(402, "余额不足，请充值");
            }

            // 调用 TTS 语音合成
            String audioBase64 = fcTtsService.synthesizeText(aiReply, null);

            int charCount = aiReply.length();
            BigDecimal ttsCost = new BigDecimal("0.0002").multiply(new BigDecimal(charCount));
            balanceService.deduct(userId, ttsCost, "tts", aiReply);

            VoiceChatResponse response = new VoiceChatResponse();
            response.setRecognizedText(recognizedText);
            response.setAiReply(aiReply);
            response.setAudioBase64(audioBase64);
            response.setBalance(userService.getBalance(userId));

            return ApiResponse.success(response);

        } catch (Exception e) {
            log.error("语音对话失败", e);
            return ApiResponse.error(500, "处理失败: " + e.getMessage());
        }
    }

    @GetMapping("/balance")
    public ApiResponse<BigDecimal> getBalance(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(userService.getBalance(userId));
    }

    @PostMapping("/chat/text")
    public ApiResponse<Map<String, String>> textChat(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        Long userId = getUserIdFromToken(httpRequest);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }

        String message = request.get("message");
        if (message == null || message.isEmpty()) {
            return ApiResponse.error(400, "消息不能为空");
        }

        try {
            User user = userService.findById(userId);
            if (user == null || user.getStatus() != 1) {
                return ApiResponse.error(403, "用户状态异常");
            }

            BigDecimal aiCost = new BigDecimal("0.002");
            if (!balanceService.deduct(userId, aiCost, "ai", message)) {
                return ApiResponse.error(402, "余额不足");
            }

            String aiReply = deepSeekService.chat(message, user.getUsername());

            // 调用 TTS 语音合成
            String audioBase64 = fcTtsService.synthesizeText(aiReply, null);

            int charCount = aiReply.length();
            BigDecimal ttsCost = new BigDecimal("0.0002").multiply(new BigDecimal(charCount));
            balanceService.deduct(userId, ttsCost, "tts", aiReply);

            Map<String, String> result = new HashMap<>();
            result.put("aiReply", aiReply);
            result.put("audioBase64", audioBase64);
            result.put("balance", userService.getBalance(userId).toString());

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("文字对话失败", e);
            return ApiResponse.error(500, "处理失败: " + e.getMessage());
        }
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
        result.put("balance", user.getBalance());
        result.put("isSponsor", user.getIsSponsor() == 1);

        return ApiResponse.success(result);
    }

    static class VoiceChatResponse {
        private String recognizedText;
        private String aiReply;
        private String audioBase64;
        private BigDecimal balance;

        public String getRecognizedText() {
            return recognizedText;
        }
        public void setRecognizedText(String recognizedText) {
            this.recognizedText = recognizedText;
        }
        public String getAiReply() {
            return aiReply;
        }
        public void setAiReply(String aiReply) {
            this.aiReply = aiReply;
        }
        public String getAudioBase64() {
            return audioBase64;
        }
        public void setAudioBase64(String audioBase64) {
            this.audioBase64 = audioBase64;
        }
        public BigDecimal getBalance() {
            return balance;
        }
        public void setBalance(BigDecimal balance) {
            this.balance = balance;
        }
    }
}