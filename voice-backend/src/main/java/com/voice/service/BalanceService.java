package com.voice.service;

import com.voice.model.User;
import com.voice.model.UsageRecord;
import com.voice.repository.UserRepository;
import com.voice.repository.UsageRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class BalanceService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UsageRecordRepository usageRecordRepository;
    
    @Transactional
    public boolean deduct(Long userId, BigDecimal amount, String type, String details) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getBalance().compareTo(amount) < 0) {
            return false;
        }
        
        user.setBalance(user.getBalance().subtract(amount));
        user.setTotalUsage(user.getTotalUsage().add(amount));
        userRepository.save(user);
        
        UsageRecord record = new UsageRecord();
        record.setUserId(userId);
        record.setType(type);
        record.setAmount(amount);
        record.setDetails(details);
        usageRecordRepository.save(record);
        
        return true;
    }
    
    @Transactional
    public void addBalance(Long userId, BigDecimal amount) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setBalance(user.getBalance().add(amount));
            userRepository.save(user);
        }
    }
}