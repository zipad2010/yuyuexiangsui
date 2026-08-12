package com.voice.service;

import com.voice.model.User;
import com.voice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Optional;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BalanceService balanceService;
    
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    public User register(String username, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名已存在");
        }
        
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setBalance(BigDecimal.ZERO);
        user.setPoints(50);
        user.setPointsMigrated(true);
        return userRepository.save(user);
    }
    
    public User login(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
                balanceService.migratePoints(user);
                userRepository.save(user);
                return user;
            }
        }
        return null;
    }
    
    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }
    
    public int getPoints(Long userId) {
        return balanceService.getPoints(userId);
    }
}