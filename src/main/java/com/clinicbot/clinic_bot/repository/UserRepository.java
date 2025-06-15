package com.clinicbot.clinic_bot.repository;

import com.clinicbot.clinic_bot.model.User;
import com.clinicbot.clinic_bot.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTelegramId(Long telegramId);
    List<User> findByRole(UserRole role);
    User findByUsername(String username);

    @Query("SELECT u FROM User u JOIN u.clinics c WHERE u.fullName = :fullName AND c.id = :clinicId")
    Optional<User> findByFullNameAndClinicId(
            @Param("fullName") String fullName,
            @Param("clinicId") Long clinicId
    );

    @Query("SELECT DISTINCT u.specialization FROM User u JOIN u.clinics c WHERE c.id = :clinicId")
    List<String> findDistinctSpecializationsByClinicId(@Param("clinicId") Long clinicId);

    @Query("SELECT u FROM User u JOIN u.clinics c WHERE c.id = :clinicId AND u.specialization = :specialization")
    List<User> findByClinicIdAndSpecialization(
            @Param("clinicId") Long clinicId,
            @Param("specialization") String specialization
    );

    @Query("SELECT u FROM User u WHERE u.fullName = :fullName")
    Optional<User> findByFullName(@Param("fullName") String fullName);

    @Query("SELECT d FROM User d JOIN d.clinics c WHERE d.role = 'DOCTOR' AND d.specialization = :specialization AND c.id = :clinicId")
    List<User> findDoctorsBySpecializationAndClinicId(
            @Param("specialization") String specialization,
            @Param("clinicId") Long clinicId
    );

    @Query("SELECT u FROM User u JOIN u.clinics c WHERE c.id = :clinicId AND u.specialization = :specialization")
    List<User> findByClinicsIdAndSpecialization(
            @Param("clinicId") Long clinicId,
            @Param("specialization") String specialization
    );

    @Query("SELECT u FROM User u WHERE u.role = 'DOCTOR'")
    List<User> findByRoleDoctor();

    @Query(value = "SELECT d.* " +
            "FROM users d " +
            "JOIN user_clinics c ON d.id = c.user_id " +
            "JOIN doctorschedule ds ON d.id = ds.doctor_id " +
            "LEFT JOIN appointment a ON a.doctor_id = d.id AND a.datetime = " +
            "    (ds.date || ' ' || ds.starttime)::timestamp " +
            "WHERE c.clinic_id = :clinicId " +
            "AND d.specialization = :specialization " +
            "AND ds.date BETWEEN :start AND :end " +
            "AND a.id IS NULL", nativeQuery = true)
    List<User> findDoctorsWithAvailableSlots(
            @Param("clinicId") Long clinicId,
            @Param("specialization") String specialization,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query(value = "SELECT DISTINCT d.specialization " +
            "FROM users d " +
            "JOIN user_clinics c ON d.id = c.user_id " +
            "JOIN doctorschedule ds ON d.id = ds.doctor_id " +
            "LEFT JOIN appointment a ON a.doctor_id = d.id AND a.datetime = " +
            "    (ds.date || ' ' || ds.starttime)::timestamp " +
            "WHERE c.clinic_id = :clinicId " +
            "AND ds.date BETWEEN :start AND :end " +
            "AND a.id IS NULL", nativeQuery = true)
    List<String> findSpecializationsWithAvailableSlots(
            @Param("clinicId") Long clinicId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query("SELECT u FROM User u WHERE u.phone = :phone")
    User findByPhone(@Param("phone") String phone);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.clinics WHERE u.id = :id")
    Optional<User> findByIdWithClinics(@Param("id") Long id);
}