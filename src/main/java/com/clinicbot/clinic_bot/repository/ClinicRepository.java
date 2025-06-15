package com.clinicbot.clinic_bot.repository;

import com.clinicbot.clinic_bot.model.Clinic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClinicRepository extends JpaRepository<Clinic, Long> {
    @Query("SELECT DISTINCT d.specialization FROM User d JOIN d.clinics c WHERE c.id = :clinicId")
    List<String> findDistinctSpecializationsByClinicId(Long clinicId);

    Optional<Clinic> findByName(String name);

    @Query("SELECT c FROM Clinic c JOIN c.doctors d WHERE d.id = :doctorId")
    List<Clinic> findByDoctorsId(@Param("doctorId") Long doctorId);
}

