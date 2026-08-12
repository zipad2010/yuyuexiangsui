package com.voice.repository;

import com.voice.model.User;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    /**
     * 按用户名或昵称模糊搜索用户（用于发起私信时从用户列表选择）
     */
    @Query("SELECT u FROM User u WHERE u.status = 1 AND "
            + "(u.username LIKE %:keyword% OR COALESCE(u.nickname, '') LIKE %:keyword%) "
            + "ORDER BY u.id ASC")
    List<User> searchByKeyword(@Param("keyword") String keyword);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}