package com.clinicbot.clinic_bot.repository;

import com.clinicbot.clinic_bot.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId")
    List<Appointment> findByPatientId(@Param("patientId") Long patientId);



    long countByPatientId(Long patientId);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id = :doctorId AND a.dateTime > :now")
    long countByDoctorIdAndDateTimeAfter(
            @Param("doctorId") Long doctorId,
            @Param("now") LocalDateTime now
    );

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND DATE(a.dateTime) = :date")
    List<Appointment> findByDoctorIdAndDate(
            @Param("doctorId") Long doctorId,
            @Param("date") LocalDate date
    );

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id = :doctorId AND a.dateTime BETWEEN :start AND :end")
    long countAppointmentsByDoctorAndDate(
            @Param("doctorId") Long doctorId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND a.dateTime BETWEEN :start AND :end")
    List<Appointment> findByDoctorIdAndDateTimeBetween(
            @Param("doctorId") Long doctorId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId")
    List<Appointment> findByDoctorId(@Param("doctorId") Long doctorId);

    List<Appointment> findByDoctorIdAndDateTimeAfter(Long doctorId, LocalDateTime dateTime);

    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId AND a.dateTime > :now")
    List<Appointment> findByPatientIdAndDateTimeAfter(
            @Param("patientId") Long patientId,
            @Param("now") LocalDateTime now
    );
}
