package com.clinicbot.clinic_bot.repository;

import com.clinicbot.clinic_bot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTelegramId(Long telegramId);

    @Modifying
    @Query("UPDATE User u SET u.role = :role WHERE u.telegramId = :telegramId")
    void updateUserRole(@Param("telegramId") Long telegramId, @Param("role") String role);
}