package com.clinicbot.clinic_bot.model;

import com.clinicbot.clinic_bot.service.RegistrationStep;

import com.clinicbot.clinic_bot.service.UserStatus;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    @Column(nullable = false)
    private String role = "USER"; // USER, ADMIN, ROOT (значение по умолчанию: USER)
    private UserStatus status = UserStatus.PENDING;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    private RegistrationStep registrationStep;

    private String specialization; // специализация врача




    public void setRegistrationStep(RegistrationStep registrationStep) {
        this.registrationStep = registrationStep;
    }

    @ManyToMany
    @JoinTable(
            name = "doctor_clinic",
            joinColumns = @JoinColumn(name = "doctor_id"),
            inverseJoinColumns = @JoinColumn(name = "clinic_id")
    )
    private Set<Clinic> clinics = new HashSet<>();

    @Enumerated(EnumType.STRING)

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTelegramId() { return telegramId; }
    public void setTelegramId(Long telegramId) { this.telegramId = telegramId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public Set<Clinic> getClinics() { return clinics; }
    public void setClinics(Set<Clinic> clinics) { this.clinics = clinics; }


    public RegistrationStep getRegistrationStep() {return registrationStep;}
    public void  setRegistrationStep(String registrationStep) {this.registrationStep = RegistrationStep.valueOf(registrationStep);}

    public String getPhone() {return phone;}
    public void setPhone(String phone) {this.phone = phone;}

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) {this.fullName = fullName;}

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
}

