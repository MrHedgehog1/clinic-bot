package com.clinicbot.clinic_bot.repository;

import com.clinicbot.clinic_bot.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
}
