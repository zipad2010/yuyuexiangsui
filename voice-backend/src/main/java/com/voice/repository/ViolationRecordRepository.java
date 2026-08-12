package com.voice.repository;

import com.voice.model.ViolationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ViolationRecordRepository extends JpaRepository<ViolationRecord, Long> {
    List<ViolationRecord> findByStatusOrderByCreatedAtDesc(Integer status);
    List<ViolationRecord> findAllByOrderByCreatedAtDesc();
}
