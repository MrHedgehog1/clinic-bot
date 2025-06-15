package com.clinicbot.clinic_bot.model;

import com.clinicbot.clinic_bot.service.RegistrationStep;
import com.clinicbot.clinic_bot.service.UserStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@SqlResultSetMapping(
        name = "UserMapping",
        entities = @EntityResult(entityClass = User.class)
)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Column(name = "chat_id", nullable = false, unique = true) // Единообразие: везде chatId
    // private Long chatId;

    @Column(name = "telegram_id", unique = true)
    private Long telegramId;


    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.PATIENT; // USER, ADMIN, ROOT (значение по умолчанию: USER)

    @Enumerated(EnumType.STRING)
    @Column(name = "adminstate")
    private AdminState adminState = AdminState.IDLE;

    @Column(unique = true)
    private String username;

    @Column(name = "specialization")
    private String specialization; // Терапевт, Хирург, и т.д.

    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.PENDING;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    private RegistrationStep registrationStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "appointmentstate")
    private AppointmentState appointmentState;

    // Выбранные параметры записи
    // Идентификатор выбранной клиники
    @Column(name = "selected_clinic_id")
    private Long selectedClinicId;
    // Выбранная специализация врача
    @Column(name = "selected_specialization")
    private String selectedSpecialization;

    // Выбранная дата приёма
    @Column(name = "selected_date")
    private LocalDate selectedDate;
    @Column(name = "selectedtime")
    private LocalTime selectedTime;

    @Column(name = "temporarycomplaints", length = 1000)
    private String temporaryComplaints; // Для хранения жалоб при записи

    @Enumerated(EnumType.STRING)
    @Column(name = "scheduleuploadstate")
    private ScheduleState scheduleUploadState; // Для отслеживания состояния загрузки расписания

    // Состояние загрузки расписания
    // @Enumerated(EnumType.STRING)
    // private ScheduleState scheduleState;

    // поле для временного хранения выбранного врача
    // @Column(name = "selected_doctor_id")
    // private Long selectedDoctorId;

    @ManyToOne
    @JoinColumn(name = "selected_doctor_id")
    private User selectedDoctor;

    @Column(name = "schedule_month")
    private LocalDate scheduleMonth;

    @ManyToMany
    @JoinTable(
            name = "user_clinics",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "clinic_id")
    )
    @BatchSize(size = 10)
    private List<Clinic> clinics = new ArrayList<>();

    public void setRegistrationStep(RegistrationStep step) {
        this.registrationStep = step;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTelegramId() { return telegramId; }
    public void setTelegramId(Long telegramId) { this.telegramId = telegramId; }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public UserRole getRole() {
        return role;
    }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public List<Clinic> getClinics() { return clinics; }
    public void setClinics(List<Clinic> clinics) { this.clinics = clinics; }


    public RegistrationStep getRegistrationStep() {return registrationStep;}
    public void  setRegistrationStep(String registrationStep) {this.registrationStep = RegistrationStep.valueOf(registrationStep);}

    public String getPhone() {return phone;}
    public void setPhone(String phone) {this.phone = phone;}

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) {this.fullName = fullName;}

    public String getUsername() { return username; }
    public void setUsername(String username) {this.username = username;}

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public Long getSelectedClinicId() {
        return selectedClinicId;
    }

    public void setSelectedClinicId(Long selectedClinicId) {
        this.selectedClinicId = selectedClinicId;
    }

    public String getSelectedSpecialization() {
        return selectedSpecialization;
    }

    public void setSelectedSpecialization(String selectedSpecialization) {
        this.selectedSpecialization = selectedSpecialization;
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(LocalDate selectedDate) {
        this.selectedDate = selectedDate;
    }

    public LocalTime getSelectedTime() { return selectedTime; }
    public void setSelectedTime(LocalTime selectedTime) { this.selectedTime = selectedTime; }

    // public String getScheduleUploadState() { return scheduleUploadState; }
    // public void setScheduleUploadState(String scheduleUploadState) { this.scheduleUploadState = scheduleUploadState; }

    public LocalDate getScheduleMonth() { return scheduleMonth; }
    public void setScheduleMonth(LocalDate scheduleMonth) { this.scheduleMonth = scheduleMonth; }

    public User getSelectedDoctor() { return selectedDoctor; }
    public void setSelectedDoctor(User selectedDoctor) { this.selectedDoctor = selectedDoctor; }

    public AdminState getAdminState() { return adminState; }
    public void setAdminState(AdminState adminState) { this.adminState = adminState; }

    public String getTemporaryComplaints() {
        return temporaryComplaints;
    }

    public void setTemporaryComplaints(String temporaryComplaints) {
        this.temporaryComplaints = temporaryComplaints;
    }

    public AppointmentState getAppointmentState() {
        return appointmentState;
    }

    public void setAppointmentState(AppointmentState appointmentState) {
        this.appointmentState = appointmentState;
    }

    public ScheduleState getScheduleUploadState() {
        return scheduleUploadState;
    }

    public void setScheduleUploadState(ScheduleState scheduleUploadState) {
        this.scheduleUploadState = scheduleUploadState;
    }


    // public String getTemporaryComplaints() { return temporaryComplaints; }
    // public void setTemporaryComplaints(String complaints) { this.temporaryComplaints = complaints; }
}

