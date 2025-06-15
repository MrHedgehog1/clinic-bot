package com.clinicbot.clinic_bot.model;

public enum AdminState {
    IDLE,
    WAITING_DOCTOR_USERNAME,
    WAITING_SCHEDULE_MONTH,
    WAITING_SCHEDULE_FILE,
    WAITING_DOCTOR_PHONE,
    MANAGING_DOCTORS
}