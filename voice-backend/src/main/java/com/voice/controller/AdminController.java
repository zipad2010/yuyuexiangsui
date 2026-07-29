package com.voice.controller;

import com.voice.model.ApiResponse;
import com.voice.model.LoginRequest;
import com.voice.model.User;
import com.voice.repository.UserRepository;
import com.voice.service.BalanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private BalanceService balanceService;
    
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";
    
    @PostMapping("/login")
    public ApiResponse<Map<String, String>> adminLogin(@RequestBody LoginRequest request) {
        if (ADMIN_USERNAME.equals(request.getUsername()) && 
            ADMIN_PASSWORD.equals(request.getPassword())) {
            Map<String, String> result = new HashMap<>();
            result.put("token", "admin_token_" + System.currentTimeMillis());
            result.put("username", ADMIN_USERNAME);
            return ApiResponse.success(result);
        }
        return ApiResponse.error(401, "用户名或密码错误");
    }
    
    @GetMapping("/dashboard/stats")
    public ApiResponse<DashboardStats> getDashboardStats() {
        DashboardStats stats = new DashboardStats();
        stats.setTotalUsers(userRepository.count());
        
        Calendar todayStart = Calendar.getInstance();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        
        // 简化版，直接返回基础数据
        List<TrendData> trends = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            TrendData trend = new TrendData();
            trend.setDate(String.format("%d-%02d", 
                Calendar.getInstance().get(Calendar.MONTH)+1, 
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - i));
            trend.setCalls(0L);
            trend.setIncome(BigDecimal.ZERO);
            trends.add(trend);
        }
        stats.setTrends(trends);
        
        return ApiResponse.success(stats);
    }
    
    @GetMapping("/users")
    public ApiResponse<PageResult<User>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        
        PageRequest pageRequest = PageRequest.of(page - 1, size);
        List<User> users = userRepository.findAll(pageRequest).getContent();
        long total = userRepository.count();
        
        PageResult<User> result = new PageResult<>();
        result.setList(users);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        
        return ApiResponse.success(result);
    }
    
    @PostMapping("/users/{userId}/recharge")
    public ApiResponse<String> rechargeUser(
            @PathVariable Long userId,
            @RequestParam BigDecimal amount,
            @RequestParam String remark) {

        int points = amount.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        balanceService.addPoints(userId, points);
        return ApiResponse.success("积分充值成功");
    }
    
    @PutMapping("/users/{userId}/status")
    public ApiResponse<String> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam int status) {
        
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        
        user.setStatus(status);
        userRepository.save(user);
        
        return ApiResponse.success(status == 1 ? "已启用" : "已禁用");
    }
    
    static class DashboardStats {
        private long totalUsers;
        private long todayCalls;
        private BigDecimal todayIncome;
        private List<TrendData> trends;
        // getters/setters...
        public long getTotalUsers() { return totalUsers; }
        public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }
        public long getTodayCalls() { return todayCalls; }
        public void setTodayCalls(long todayCalls) { this.todayCalls = todayCalls; }
        public BigDecimal getTodayIncome() { return todayIncome; }
        public void setTodayIncome(BigDecimal todayIncome) { this.todayIncome = todayIncome; }
        public List<TrendData> getTrends() { return trends; }
        public void setTrends(List<TrendData> trends) { this.trends = trends; }
    }
    
    static class TrendData {
        private String date;
        private long calls;
        private BigDecimal income;
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public long getCalls() { return calls; }
        public void setCalls(long calls) { this.calls = calls; }
        public BigDecimal getIncome() { return income; }
        public void setIncome(BigDecimal income) { this.income = income; }
    }
    
    static class PageResult<T> {
        private List<T> list;
        private long total;
        private int page;
        private int size;
        public List<T> getList() { return list; }
        public void setList(List<T> list) { this.list = list; }
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
    }
}