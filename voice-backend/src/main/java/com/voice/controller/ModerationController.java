package com.voice.controller;

import com.voice.config.JwtUtil;
import com.voice.model.ApiResponse;
import com.voice.model.ViolationRecord;
import com.voice.repository.ViolationRecordRepository;
import com.voice.service.ContentReviewService;
import com.voice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 管理中心：违规记录查询与处理。
 * 仅管理员（数据库中 username=zipad 的账户）可用，非管理员一律返回 403。
 */
@RestController
@RequestMapping("/api/moderation")
@CrossOrigin(origins = "*")
public class ModerationController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ContentReviewService contentReviewService;

    @Autowired
    private ViolationRecordRepository violationRecordRepository;

    @Autowired
    private UserService userService;

    private Long getUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }

    /** 当前登录用户是否为管理员（供前端控制菜单显示） */
    @GetMapping("/is-admin")
    public ApiResponse<Boolean> isAdmin(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        return ApiResponse.success(contentReviewService.isAdmin(userId));
    }

    /** 违规记录列表（仅管理员） */
    @GetMapping("/violations")
    public ApiResponse<List<ViolationRecord>> getViolations(
            @RequestParam(defaultValue = "0") int status,
            HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (!contentReviewService.isAdmin(userId)) {
            return ApiResponse.error(403, "无权限");
        }
        List<ViolationRecord> records;
        if (status == 1) {
            records = violationRecordRepository.findAllByOrderByCreatedAtDesc();
        } else {
            records = violationRecordRepository.findByStatusOrderByCreatedAtDesc(0);
        }
        return ApiResponse.success(records);
    }

    /** 标记违规记录为已处理（仅管理员） */
    @PostMapping("/violations/{recordId}/handle")
    public ApiResponse<String> handleViolation(@PathVariable Long recordId,
                                               HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (!contentReviewService.isAdmin(userId)) {
            return ApiResponse.error(403, "无权限");
        }
        ViolationRecord record = violationRecordRepository.findById(recordId).orElse(null);
        if (record == null) {
            return ApiResponse.error(404, "记录不存在");
        }
        record.setStatus(1);
        violationRecordRepository.save(record);
        return ApiResponse.success("已处理");
    }

    /** 禁用违规用户（仅管理员） */
    @PostMapping("/violations/{recordId}/disable-user")
    public ApiResponse<String> disableUser(@PathVariable Long recordId,
                                           HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (!contentReviewService.isAdmin(userId)) {
            return ApiResponse.error(403, "无权限");
        }
        ViolationRecord record = violationRecordRepository.findById(recordId).orElse(null);
        if (record == null || record.getUserId() == null) {
            return ApiResponse.error(404, "记录不存在");
        }
        com.voice.model.User user = userService.findById(record.getUserId());
        if (user != null) {
            user.setStatus(0);
            userService.getUserRepository().save(user);
            record.setStatus(1);
            violationRecordRepository.save(record);
            return ApiResponse.success("用户已禁用");
        }
        return ApiResponse.error(404, "用户不存在");
    }
}
