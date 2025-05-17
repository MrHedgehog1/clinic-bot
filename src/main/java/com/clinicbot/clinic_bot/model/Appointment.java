package com.clinicbot.clinic_bot.model;


import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User patient;

    @ManyToOne
    private User doctor;

    private LocalDateTime dateTime;
    private String status; // PLANNED, COMPLETED, CANCELED

    // Геттеры и сеттеры
}
