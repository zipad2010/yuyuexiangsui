package com.voice.service;

import com.voice.model.User;
import com.voice.model.UsageRecord;
import com.voice.repository.UserRepository;
import com.voice.repository.UsageRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class BalanceService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UsageRecordRepository usageRecordRepository;
    
    @Transactional
    public boolean deductPoint(Long userId, String type, String details) {
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) {
            return false;
        }
        migratePoints(user);
        if (user.getPoints() < 1) {
            return false;
        }

        user.setPoints(user.getPoints() - 1);
        // total_usage 对老用户可能为 NULL，需 null 安全累加
        user.setTotalUsage(user.getTotalUsage() == null
                ? BigDecimal.ONE
                : user.getTotalUsage().add(BigDecimal.ONE));
        userRepository.save(user);
        
        UsageRecord record = new UsageRecord();
        record.setUserId(userId);
        record.setType(type);
        record.setAmount(BigDecimal.ONE);
        record.setDetails(details);
        usageRecordRepository.save(record);
        
        return true;
    }

    @Transactional
    public void refundPoint(Long userId, String details) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            migratePoints(user);
            user.setPoints(user.getPoints() + 1);
            userRepository.save(user);

            UsageRecord record = new UsageRecord();
            record.setUserId(userId);
            record.setType("refund");
            record.setAmount(BigDecimal.ONE.negate());
            record.setDetails(details);
            usageRecordRepository.save(record);
        }
    }

    @Transactional
    public void addPoints(Long userId, int points) {
        if (points <= 0) {
            throw new IllegalArgumentException("积分必须大于 0");
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            migratePoints(user);
            user.setPoints(user.getPoints() + points);
            userRepository.save(user);
        }
    }

    @Transactional
    public int getPoints(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return 0;
        }
        migratePoints(user);
        userRepository.save(user);
        return user.getPoints();
    }

    public void migratePoints(User user) {
        if (Boolean.TRUE.equals(user.getPointsMigrated()) && user.getPoints() != null) {
            return;
        }
        BigDecimal legacyBalance = user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
        int convertedPoints = legacyBalance.multiply(new BigDecimal("5"))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        user.setPoints(Math.max(0, convertedPoints));
        user.setPointsMigrated(true);
    }
}