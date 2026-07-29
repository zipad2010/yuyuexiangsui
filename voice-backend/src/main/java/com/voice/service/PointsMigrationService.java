package com.voice.service;

import com.voice.model.User;
import com.voice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class PointsMigrationService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BalanceService balanceService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateLegacyBalances() {
        List<User> users = userRepository.findAll();
        boolean changed = false;
        for (User user : users) {
            if (!Boolean.TRUE.equals(user.getPointsMigrated()) || user.getPoints() == null) {
                balanceService.migratePoints(user);
                changed = true;
            }
        }
        if (changed) {
            userRepository.saveAll(users);
        }
    }
}