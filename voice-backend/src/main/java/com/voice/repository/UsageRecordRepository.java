package com.voice.repository;

import com.voice.model.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {
    List<UsageRecord> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    @Query("SELECT SUM(u.amount) FROM UsageRecord u WHERE u.userId = :userId AND u.createdAt >= :start")
    BigDecimal sumAmountByUserIdAndDateAfter(@Param("userId") Long userId, @Param("start") Date start);
    
    @Query("SELECT COUNT(u) FROM UsageRecord u WHERE u.userId = :userId AND u.createdAt >= :start")
    Long countByUserIdAndDateAfter(@Param("userId") Long userId, @Param("start") Date start);
}