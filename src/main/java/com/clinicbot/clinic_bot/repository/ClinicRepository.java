package com.clinicbot.clinic_bot.repository;

import com.clinicbot.clinic_bot.model.Clinic;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {
}

