package com.clinicbot.clinic_bot.repository;

import com.clinicbot.clinic_bot.model.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    @Query("SELECT DISTINCT ds.date FROM DoctorSchedule ds WHERE ds.doctor.id = :doctorId")
    List<LocalDate> findDistinctDatesByDoctorId(@Param("doctorId") Long doctorId);

    @Query("SELECT ds.startTime FROM DoctorSchedule ds WHERE ds.doctor.id = :doctorId AND ds.date = :date")
    List<LocalTime> findAvailableTimesByDoctorIdAndDate(
            @Param("doctorId") Long doctorId,
            @Param("date") LocalDate date
    );

    @Query("SELECT ds FROM DoctorSchedule ds WHERE ds.doctor.id = :doctorId AND ds.date BETWEEN :start AND :end")
    List<DoctorSchedule> findByDoctorIdAndDateRange(
            @Param("doctorId") Long doctorId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Modifying
    @Query("DELETE FROM DoctorSchedule ds WHERE ds.doctor.id = :doctorId AND ds.date BETWEEN :start AND :end")
    void deleteByDoctorIdAndDateRange(
            @Param("doctorId") Long doctorId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query("SELECT ds FROM DoctorSchedule ds WHERE ds.doctor.id = :doctorId AND ds.date = :date")
    List<DoctorSchedule> findByDoctorIdAndDate(
            @Param("doctorId") Long doctorId,
            @Param("date") LocalDate date
    );

    @Query("SELECT ds FROM DoctorSchedule ds WHERE ds.doctor.id = :doctorId AND ds.date BETWEEN :start AND :end")
    List<DoctorSchedule> findByDoctorIdAndDateBetween(
            @Param("doctorId") Long doctorId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    // Подсчет слотов по дате в диапазоне
    long countByDateBetween(LocalDate start, LocalDate end);

    // Подсчет слотов для конкретного врача в диапазоне дат
    long countByDoctorIdAndDateBetween(Long doctorId, LocalDate start, LocalDate end);
}
