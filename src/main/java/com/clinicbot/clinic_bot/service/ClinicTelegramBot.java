package com.clinicbot.clinic_bot.service;

import com.clinicbot.clinic_bot.model.*;
import com.clinicbot.clinic_bot.model.User;
import com.clinicbot.clinic_bot.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.FileInputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static java.awt.SystemColor.text;

// import static java.util.stream.Nodes.collect;

@Slf4j
@Component
public class ClinicTelegramBot extends TelegramWebhookBot {
    // Константы состояний

    private UserRepository userRepository;
    private ClinicRepository clinicRepository;
    private DoctorScheduleRepository doctorScheduleRepository;
    private AppointmentRepository appointmentRepository;

    @Value("${bot.token}")
    private String botToken;

    @Value("${bot.username}")
    private String botUsername;

    @Value("${bot.path}")
    private String botPath;

    public ClinicTelegramBot() {
        // Этот конструктор нужен для работы @Lazy
    }

    @Autowired
    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Autowired
    public void setClinicRepository(ClinicRepository clinicRepository) {
        this.clinicRepository = clinicRepository;
    }

    @Autowired
    public void setDoctorScheduleRepository(DoctorScheduleRepository doctorScheduleRepository) {
        this.doctorScheduleRepository = doctorScheduleRepository;
    }

    @Autowired
    public void setAppointmentRepository(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        log.debug("Received update: {}", update);
        try {
            // Обработка callback-запросов (нажатие на inline-кнопки)
            if (update.hasCallbackQuery()) {
                return handleCallbackQuery(update.getCallbackQuery());
            }

            // Обработка обычных сообщений
            if (update == null || !update.hasMessage()) {
                log.warn("Received empty update or update without message");
                return null;
            }
            if (!update.hasMessage()) return null;

            Long chatId = update.getMessage().getChatId();
            String text = update.getMessage().hasText() ? update.getMessage().getText() : "";
            if (!(text instanceof String)) {
                log.error("Received non-string text: {}", text);
                return sendMessage(update.getMessage().getChatId(), "⚠️ Некорректная команда");
            }

            // Обработка /start
            if (text.equals("/start")) {
                return handleStartCommand(update);
            }

            Optional<User> userOpt = userRepository.findByTelegramId(chatId);

            // Обработка этапов регистрации
            if (userOpt.isPresent() && userOpt.get().getRegistrationStep() != RegistrationStep.COMPLETED) {
                return handleRegistrationStep(userOpt.get(), text, update);
            }

            // Обработка загрузки расписания
            if (userOpt.isPresent() &&
                    (userOpt.get().getAdminState() == AdminState.WAITING_SCHEDULE_MONTH ||
                            userOpt.get().getAdminState() == AdminState.WAITING_SCHEDULE_FILE)) {
                return handleAdminState(userOpt.get(), update);
            }

            // Обработка действий после регистрации
            if (userOpt.isPresent() && userOpt.get().getRegistrationStep() == RegistrationStep.COMPLETED) {
                return handlePostRegistration(userOpt.get(), text, update);
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
            return createErrorMessage(update);
        }

        return null;
    }

    private BotApiMethod<?> createErrorMessage(Update update) {
        Long chatId = null;
        if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
        } else if (update.hasMessage()) {
            chatId = update.getMessage().getChatId();
        }

        if (chatId != null) {
            return SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⚠️ Произошла внутренняя ошибка. Попробуйте позже.")
                    .build();
        }
        return null;
    }

    private BotApiMethod<?> handleAdminState(User admin, Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            log.warn("Admin state handling requires text message");
            return null;
        }

        String text = update.getMessage().getText();

        // Проверяем, что текст является строкой
        if (!(text instanceof String)) {
            log.error("Non-string command in admin state: {}", text);
            resetAdminState(admin);
            return sendMessage(admin.getTelegramId(), "⚠️ Некорректная команда");
        }
        log.info("Handling admin state: {} for command: {}", admin.getAdminState(), text);
        // Проверяем, что текст является строкой
        if (!(text instanceof String)) {
            log.error("Non-string command in admin state: {}", text);
            resetAdminState(admin);
            return sendMessage(admin.getTelegramId(), "⚠️ Некорректная команда");
        }
        // Сбрасываем состояние при выборе любой команды главного меню
        List<String> mainMenuCommands = Arrays.asList(
                "Запись к врачу", "Мои записи", "Мой профиль",
                "Загрузить расписание", "Назначить врача",
                "Управление клиниками", "Управление врачами"
        );

        // Обработка кнопки "Назад"
        if ("🔙 Назад".equals(text)) {
            resetAdminState(admin);
            return SendMessage.builder()
                    .chatId(admin.getTelegramId().toString())
                    .text("Действие отменено")
                    .replyMarkup(createMainMenuKeyboard(admin))
                    .build();
        }

        // Обработка кнопки "Главное меню"
        if ("🏠 Главное меню".equals(text)) {
            resetAdminState(admin);
            return SendMessage.builder()
                    .chatId(admin.getTelegramId().toString())
                    .text("Возвращаемся в главное меню")
                    .replyMarkup(createMainMenuKeyboard(admin))
                    .build();
        }

        switch (admin.getAdminState()) {
            case WAITING_DOCTOR_PHONE:
                return assignDoctorRole(admin, update.getMessage().getText());
            case WAITING_SCHEDULE_MONTH:
                return handleScheduleMonthInput(admin, update.getMessage().getText());
            case WAITING_SCHEDULE_FILE:
                return handleScheduleUploadState(admin, update);
            default:
                resetAdminState(admin);
                return sendMessage(admin.getTelegramId(), "Неизвестное состояние администратора");
        }
    }

    private void resetAdminState(User admin) {
        log.info("Resetting admin state for user: {}", admin.getId());
        admin.setAdminState(AdminState.IDLE);
        admin.setScheduleMonth(null);
        admin.setScheduleUploadState(null);
        userRepository.save(admin);
    }

    private BotApiMethod<?> handleStartCommand(Update update) {
        Message message = update.getMessage();
        if (message == null) {
            log.warn("Received update without message");
            return null;
        }

        Long chatId = update.getMessage().getChatId();
        String username = update.getMessage().getFrom().getUserName(); // получаем username

        Optional<User> userOpt = userRepository.findByTelegramId(chatId);
        SendMessage response = new SendMessage();
        response.setChatId(chatId.toString());

        if (userOpt.isEmpty()) {
            User newUser = new User();
            newUser.setTelegramId(chatId);
            // Безопасная установка username
            if (username != null && !username.isBlank()) {
                newUser.setUsername(username);// сохраняем username
            } else {
                log.warn("Username is null for chatId: {}", chatId);
            }
            newUser.setRegistrationStep(RegistrationStep.ENTER_PHONE);
            newUser.setStatus(UserStatus.PENDING);
            newUser.setRole(UserRole.PATIENT); // используем enum
            userRepository.save(newUser);

            response.setText("Для регистрации нажмите кнопку «Отправить номер»:");
            response.setReplyMarkup(createPhoneRequestKeyboard());
        } else {
            User user = userOpt.get();

            // Обновляем информацию о пользователе из базы
            user = userRepository.findById(user.getId()).orElse(user);

            // Сбрасываем состояние записи при команде /start
            resetAppointmentState(user);
            resetAdminState(user);

            if (user.getRegistrationStep() == RegistrationStep.COMPLETED) {
                response.setText("Вы уже зарегистрированы! Выберите действие:");
                response.setReplyMarkup(createMainMenuKeyboard(user));
            } else {
                // Продолжение регистрации
                switch (user.getRegistrationStep()) {
                    case ENTER_PHONE:
                        response.setText("Для регистрации нажмите кнопку «Отправить номер»:");
                        response.setReplyMarkup(createPhoneRequestKeyboard());
                        break;
                    case ENTER_FULL_NAME:
                        response.setText("Введите ваше полное имя (ФИО):");
                        break;
                }
            }
        }
        return response;
    }

    private BotApiMethod<?> handleRegistrationStep(User user, String text, Update update) {
        try {
            SendMessage response = new SendMessage();
            response.setChatId(user.getTelegramId().toString());

            switch (user.getRegistrationStep()) {
                case ENTER_PHONE:
                    if (update.getMessage() != null && update.getMessage().hasContact()) {
                        Contact contact = update.getMessage().getContact();
                        if (contact != null && contact.getPhoneNumber() != null) {
                            user.setPhone(contact.getPhoneNumber());
                            user.setRegistrationStep(RegistrationStep.ENTER_FULL_NAME);
                            response.setText("Введите ваше полное имя (ФИО):");
                        } else {
                            // Если контакт не получен
                            response.setText("Не удалось получить номер телефона. Пожалуйста, попробуйте еще раз.");
                            response.setReplyMarkup(createPhoneRequestKeyboard());
                        }
                    } else {
                        // Если сообщение не содержит контакт
                        response.setText("Для регистрации нажмите кнопку «Отправить номер»:");
                        response.setReplyMarkup(createPhoneRequestKeyboard());
                    }
                    break;

                case ENTER_FULL_NAME:
                    if (text != null && !text.trim().isEmpty()) {
                        user.setFullName(text.trim());
                        user.setRegistrationStep(RegistrationStep.COMPLETED);
                        user.setStatus(UserStatus.ACTIVE);
                        response.setText("Регистрация завершена! Выберите действие:");
                        response.setReplyMarkup(createMainMenuKeyboard(user));
                    } else {
                        response.setText("Имя не может быть пустым. Введите ваше полное имя (ФИО):");
                    }
                    break;
            }

            userRepository.save(user);
            return response;
        } catch (Exception e) {
            log.error("Error in handleRegistrationStep for user: {}", user.getId(), e);
            return SendMessage.builder()
                    .chatId(user.getTelegramId().toString())
                    .text("⚠️ Произошла ошибка при обработке регистрации. Попробуйте позже.")
                    .build();
        }
    }

    private BotApiMethod<?> handlePostRegistration(User user, String text, Update update) {
        // Приоритет 1: Обработка состояний администратора
        if ((user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.ROOT) &&
                user.getAdminState() != null &&
                user.getAdminState() != AdminState.IDLE) {
            return handleAdminState(user, update);
        }

        // Приоритет 2: Процесс записи на прием
        if (user.getAppointmentState() != null) {
            return handleAppointmentState(user, text, update);
        }

        // Сбрасываем состояние записи при смене роли
        if (user.getAppointmentState() != null && !text.equals("Запись к врачу")) {
            resetAppointmentState(user);
        }


        // Приоритет 3: Основные команды
        switch (user.getRole()) {
            case ADMIN, ROOT -> {
                return handleAdminCommands(user, text);
            }
            case DOCTOR -> {
                return handleDoctorCommands(user, text);
            }
            default -> {
                return handlePatientCommands(user, text);
            }
        }
    }

    private BotApiMethod<?> assignDoctorRole(User admin, String phone) {
        try {
            // Нормализация номера телефона
            String normalizedPhone = normalizePhone(phone);

            if (normalizedPhone == null) {
                return sendMessage(admin.getTelegramId(), "Неверный формат номера телефона. Используйте формат +7XXXXXXXXXX");
            }

            User doctor = userRepository.findByPhone(normalizedPhone);
            if (doctor == null) {
                return sendMessage(admin.getTelegramId(), "Пользователь с номером " + normalizedPhone + " не найден");
            }

            doctor.setRole(UserRole.DOCTOR);
            userRepository.save(doctor);

            resetAdminState(admin);
            return sendMessage(admin.getTelegramId(), "✅ Пользователь " + doctor.getFullName() + " назначен врачом");
        } catch (Exception e) {
            log.error("Error assigning doctor role", e);
            return SendMessage.builder()
                    .chatId(admin.getTelegramId().toString())
                    .text("⚠️ Ошибка при назначении врача: " + e.getMessage())
                    .replyMarkup(createMainMenuKeyboard(admin))
                    .build();
        }

    }

    // Вспомогательный метод для нормализации номера
    private String normalizePhone(String phone) {
        if (phone == null) return null;

        // Удаляем все нецифровые символы, кроме плюса
        String cleanPhone = phone.replaceAll("[^+0-9]", "");

        // Российские номера: преобразуем 89... в +79...
        if (cleanPhone.startsWith("89") && cleanPhone.length() == 11) {
            return "+7" + cleanPhone.substring(1);
        }

        // Международный формат
        if (cleanPhone.startsWith("+7") && cleanPhone.length() == 12) {
            return cleanPhone;
        }

        // Неподдерживаемый формат
        return null;
    }

    private ReplyKeyboardMarkup createBackButtonKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        row.add("🔙 Назад");
        row.add("❌ Отмена записи");
        row.add("🏠 Главное меню");

        keyboard.setKeyboard(List.of(row));
        return keyboard;
    }

    private ReplyKeyboardMarkup createAdminBackKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        row.add("🔙 Назад");
        row.add("🏠 Главное меню");

        keyboard.setKeyboard(List.of(row));
        return keyboard;
    }

    // Реализация основных команд для пациентов
    private BotApiMethod<?> handlePatientCommands(User user, String text) {
        // Сброс состояния при выборе любой команды кроме записи
        if (!text.equals("Запись к врачу") && user.getAppointmentState() != null) {
            resetAppointmentState(user);
        }

        switch (text) {
            case "Запись к врачу":
                return startAppointmentProcess(user);
            case "Мои записи":
                return showUserAppointments(user);
            case "Мой профиль":
                return showUserProfile(user);
            default:
                return sendMessage(user.getTelegramId(), "Выберите действие из меню:");
        }
    }

    // Реализация основных команд для врачей
    private BotApiMethod<?> handleDoctorCommands(User user, String text) {
        switch (text) {
            case "Мое расписание":
                return showDoctorSchedule(user);
            case "Выбрать специальность":
                return handleDoctorSpecializationSelection(user);
            case "Мои пациенты":
                return showDoctorPatients(user);
            case "Записи на сегодня":
                return showTodaysAppointments(user);
            case "Мой профиль":
                return showUserProfile(user);
            default:
                return handlePatientCommands(user, text); // Доктор может использовать функции пациента
        }
    }

    // Реализация основных команд для администраторов
    private BotApiMethod<?> handleAdminCommands(User user, String text) {
        // Проверка типа текста
        if (!(text instanceof String)) {
            log.error("Non-string command in admin commands: {}", text);
            return sendMessage(user.getTelegramId(), "⚠️ Некорректная команда");
        }

        resetAdminState(user);

        switch (text) {
            case "Назначить врача":
                return startAssignDoctorProcess(user);
            case "Загрузить расписание":
                return startScheduleUploadProcess(user);
            case "Управление клиниками":
                return manageClinics(user);
            case "Управление врачами":
                return manageDoctors(user);
            case "/add_test_data":
                return addTestData(user);
            case "Мой профиль":
                return showUserProfile(user);
            default:
                return handleDoctorCommands(user, text); // Админ может использовать функции врача
        }
    }

    private BotApiMethod<?> addTestData(User admin) {
        // Добавление тестовой клиники
        Clinic clinic = new Clinic();
        clinic.setName("Тестовая клиника");
        clinic.setAddress("ул. Тестовая, 1");
        clinicRepository.save(clinic);

        // Добавление тестового врача
        User doctor = new User();
        doctor.setFullName("Тестовый Врач");
        doctor.setRole(UserRole.DOCTOR);
        doctor.setSpecialization("Терапевт");
        doctor.setStatus(UserStatus.ACTIVE);
        doctor.getClinics().add(clinic);
        userRepository.save(doctor);

        // Добавление расписания
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setClinic(clinic);
        schedule.setDate(LocalDate.now().plusDays(1));
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(12, 0));
        schedule.setSlotDuration(30);
        doctorScheduleRepository.save(schedule);

        return sendMessage(admin.getTelegramId(), "✅ Тестовые данные добавлены!");
    }

    private BotApiMethod<?> startAssignDoctorProcess(User admin) {
        try {
            admin.setAdminState(AdminState.WAITING_DOCTOR_PHONE);
            userRepository.save(admin);
            return SendMessage.builder()
                    .chatId(admin.getTelegramId().toString())
                    .text("Введите номер телефона врача (в формате +7XXXXXXXXXX):")
                    .replyMarkup(createAdminBackKeyboard())
                    .build();
        } catch (Exception e) {
            log.error("Error setting admin state", e);
            return SendMessage.builder()
                    .chatId(admin.getTelegramId().toString())
                    .text("⚠️ Ошибка при инициализации процесса. Попробуйте позже.")
                    .build();
        }
    }

    // === Реализация процесса записи на прием ===

    private BotApiMethod<?> startAppointmentProcess(User user) {
        // Проверяем наличие клиник
        List<Clinic> clinics = clinicRepository.findAll();
        if (clinics.isEmpty()) {
            return SendMessage.builder()
                    .chatId(user.getTelegramId().toString())
                    .text("⚠️ В системе пока нет доступных клиник.\n\n"
                            + "Пожалуйста, обратитесь к администратору или попробуйте позже.")
                    .build();
        }

        // Проверяем наличие врачей
        List<User> doctors = userRepository.findByRole(UserRole.DOCTOR);
        if (doctors.isEmpty()) {
            return SendMessage.builder()
                    .chatId(user.getTelegramId().toString())
                    .text("⚠️ В системе пока нет врачей.\n\n"
                            + "Администратор должен добавить врачей и загрузить расписание.")
                    .build();
        }

        // Проверяем наличие расписания
        // Улучшенная проверка доступных слотов
        long availableSlots = doctorScheduleRepository.countByDateBetween(
                LocalDate.now(),
                LocalDate.now().plusMonths(1)
        );

        if (availableSlots == 0) {
            return SendMessage.builder()
                    .chatId(user.getTelegramId().toString())
                    .text("⚠️ На ближайший месяц нет доступных записей.\n\n" +
                            "Пожалуйста, попробуйте позже или обратитесь к администратору.")
                    .build();
        }

        user.setAppointmentState(AppointmentState.SELECT_CLINIC);
        userRepository.save(user);

        return createSelectionMenu(
                user.getTelegramId(),
                "Выберите клинику:",
                clinics.stream().map(Clinic::getName).collect(Collectors.toList())
        );
    }

    private BotApiMethod<?> handleAppointmentState(User user, String text, Update update) {
        try {
            // Проверяем, не хочет ли пользователь вернуться в главное меню
            if ("🏠 Главное меню".equals(text)) {
                resetAppointmentState(user);
                return SendMessage.builder()
                        .chatId(user.getTelegramId().toString())
                        .text("Возвращаемся в главное меню")
                        .replyMarkup(createMainMenuKeyboard(user))
                        .build();
            }
            // Если это не кнопка "Главное меню", обрабатываем текущее состояние
            switch (user.getAppointmentState()) {
                case SELECT_CLINIC:
                    return handleClinicSelection(user, text);
                case MANAGE_CLINICS:
                    return handleClinicManagement(user, text);
                case SELECT_SPECIALIZATION:
                    return handleSpecializationSelection(user, text);
                case SELECT_DOCTOR:
                    return handleDoctorSelection(user, text);
                case SELECT_DAY:
                    return handleDaySelection(user, text);
                case SELECT_TIME:
                    return handleTimeSelection(user, text);
                case ENTER_COMPLAINTS:
                    return handleComplaintsInput(user, text);
                case CONFIRM_APPOINTMENT:
                    return handleAppointmentConfirmation(user, text);
                case MANAGE_DOCTORS:
                    return handleDoctorManagement(user, text);
                case ADD_DOCTOR:
                    return handleAddDoctor(user, text);
                case EDIT_DOCTOR:
                    return handleEditDoctor(user, text);
                case DELETE_DOCTOR:
                    return handleDeleteDoctor(user, text);
                case EDIT_DOCTOR_FULLNAME:
                    return handleEditDoctorFullName(user, text);
                case EDIT_DOCTOR_SPECIALIZATION:
                    return handleEditDoctorSpecialization(user, text);
                case EDIT_DOCTOR_CLINICS:
                    return handleEditDoctorClinics(user, text);
                default:
                    resetAppointmentState(user);
                    return SendMessage.builder()
                            .chatId(user.getTelegramId().toString())
                            .text("Неизвестное состояние. Возвращаемся в главное меню.")
                            .replyMarkup(createMainMenuKeyboard(user))
                            .build();
            }
        } catch (Exception e) {
            log.error("Error in appointment process", e);
            resetAppointmentState(user);
            return SendMessage.builder()
                    .chatId(user.getTelegramId().toString())
                    .text("⚠️ Произошла ошибка при обработке записи. Пожалуйста, начните заново.")
                    .replyMarkup(createMainMenuKeyboard(user)) // Возвращаем в главное меню
                    .build();
        }
    }

    private BotApiMethod<?> handleClinicSelection(User user, String clinicName) {
        try {
            Optional<Clinic> clinicOpt = clinicRepository.findByName(clinicName);
            if (clinicOpt.isEmpty()) {
                return sendMessage(user.getTelegramId(), "Клиника не найдена. Выберите из списка:");
            }

            user.setSelectedClinicId(clinicOpt.get().getId());
            user.setAppointmentState(AppointmentState.SELECT_SPECIALIZATION);
            userRepository.save(user);

            // Получаем ВСЕ специализации в клинике
            List<String> allSpecializations = userRepository.findDistinctSpecializationsByClinicId(user.getSelectedClinicId());

            // Получаем специализации СО СВОБОДНЫМИ СЛОТАМИ
            List<String> availableSpecializations = userRepository.findSpecializationsWithAvailableSlots(
                    user.getSelectedClinicId(),
                    LocalDate.now(),
                    LocalDate.now().plusMonths(1)
            );

            // Формируем список с пометками о доступности
            List<String> menuOptions = new ArrayList<>();
            for (String spec : allSpecializations) {
                if (availableSpecializations.contains(spec)) {
                    menuOptions.add(spec);
                } else {
                    menuOptions.add(spec + " (нет слотов)");
                }
            }

            return createSelectionMenu(
                    user.getTelegramId(),
                    "Выберите специализацию врача:",
                    menuOptions
            );
        } catch (Exception e) {
            log.error("Error in clinic selection for user: {}", user.getId(), e);
            return SendMessage.builder()
                    .chatId(user.getTelegramId().toString())
                    .text("⚠️ Ошибка при выборе клиники: " + e.getMessage())
                    .build();
        }

    }

    private BotApiMethod<?> handleSpecializationSelection(User user, String specialization) {
        user.setSelectedSpecialization(specialization);
        user.setAppointmentState(AppointmentState.SELECT_DOCTOR);
        userRepository.save(user);

        // Получаем врачей с доступными слотами
        List<User> doctors = userRepository.findDoctorsWithAvailableSlots(
                user.getSelectedClinicId(),
                specialization,
                LocalDate.now(),
                LocalDate.now().plusMonths(1)
        );

        if (doctors.isEmpty()) {
            return SendMessage.builder()
                    .chatId(user.getTelegramId().toString())
                    .text("⚠️ У врачей специализации \"" + specialization + "\" нет свободных записей.\n" +
                            "Пожалуйста, выберите другую специализацию.")
                    .replyMarkup(createBackButtonKeyboard())
                    .build();
        }

        // Убираем дубликаты врачей
        Map<Long, User> uniqueDoctors = new LinkedHashMap<>();
        for (User doctor : doctors) {
            uniqueDoctors.putIfAbsent(doctor.getId(), doctor);
        }
        List<User> uniqueDoctorsList = new ArrayList<>(uniqueDoctors.values());

        // Формируем список для кнопок
        List<String> doctorOptions = new ArrayList<>();
        StringBuilder message = new StringBuilder("Выберите врача:\n\n");

        for (User doctor : uniqueDoctorsList) {
            long freeSlots = calculateFreeSlots(doctor);
            doctorOptions.add(doctor.getFullName()); // Только имя без эмодзи
            message.append("👨‍⚕️ ")
                    .append(doctor.getFullName())
                    .append(" - свободно: ")
                    .append(freeSlots)
                    .append("\n");
        }

        return SendMessage.builder()
                .chatId(user.getTelegramId().toString())
                .text(message.toString())
                .replyMarkup(createKeyboard(doctorOptions))
                .build();
    }

    private long calculateFreeSlots(User doctor) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(1);

        // Получаем все слоты врача
        List<DoctorSchedule> allSlots = doctorScheduleRepository.findByDoctorIdAndDateBetween(
                doctor.getId(),
                startDate,
                endDate
        );

        // Получаем занятые записи
        List<Appointment> bookedAppointments = appointmentRepository.findByDoctorIdAndDateTimeBetween(
                doctor.getId(),
                startDate.atStartOfDay(),
                endDate.atTime(LocalTime.MAX)
        );

        return allSlots.stream()
                .filter(slot -> isSlotAvailable(slot, bookedAppointments))
                .count();
    }

    private boolean isSlotAvailable(DoctorSchedule slot, List<Appointment> appointments) {
        return appointments.stream().noneMatch(appt ->
                appt.getDateTime().toLocalDate().equals(slot.getDate()) &&
                        appt.getDateTime().toLocalTime().equals(slot.getStartTime())
        );
    }

    private BotApiMethod<?> handleDoctorSelection(User user, String doctorName) {
        try {
            // Теперь мы получаем чистое имя врача
            log.info("Selected doctor name: {}", doctorName);

            // Ищем врача по имени и клинике
            Optional<User> doctorOpt = userRepository.findByFullNameAndClinicId(
                    doctorName,
                    user.getSelectedClinicId()
            );

            if (doctorOpt.isEmpty()) {
                log.warn("Doctor not found: {} for clinic: {}", doctorName, user.getSelectedClinicId());
                return sendMessage(user.getTelegramId(), "Врач не найден. Выберите из списка:");
            }

            user.setSelectedDoctor(doctorOpt.get());
            user.setAppointmentState(AppointmentState.SELECT_DAY);
            userRepository.save(user);

            return showCalendar(user);
        } catch (Exception e) {
            log.error("Error in doctor selection", e);
            return sendMessage(user.getTelegramId(), "Ошибка при выборе врача. Попробуйте снова.");
        }
    }

    private BotApiMethod<?> showCalendar(User user) {
        YearMonth currentMonth = YearMonth.now();
        LocalDate startDate = currentMonth.atDay(1);
        LocalDate endDate = currentMonth.atEndOfMonth();

        // Проверяем наличие слотов в месяце
        long slotsThisMonth = doctorScheduleRepository.countByDoctorIdAndDateBetween(
                user.getSelectedDoctor().getId(),
                startDate,
                endDate
        );

        if (slotsThisMonth == 0) {
            return SendMessage.builder()
                    .chatId(user.getTelegramId().toString())
                    .text("⚠️ У выбранного врача нет расписания на этот месяц.\n" +
                            "Пожалуйста, выберите другого врача.")
                    .replyMarkup(createBackButtonKeyboard())
                    .build();
        }

        LocalDate firstDay = currentMonth.atDay(1);
        int daysInMonth = currentMonth.lengthOfMonth();
        int firstDayOfWeek = firstDay.getDayOfWeek().getValue(); // 1-пн, 7-вс

        // Создаем клавиатуру-календарь
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();

        // Заголовок месяца
        KeyboardRow monthRow = new KeyboardRow();
        monthRow.add(currentMonth.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"))
                + " " + currentMonth.getYear());
        rows.add(monthRow);

        // Дни недели
        KeyboardRow weekDaysRow = new KeyboardRow();
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            weekDaysRow.add(dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, new Locale("ru")));
        }
        rows.add(weekDaysRow);

        // Заполняем календарь
        KeyboardRow currentRow = new KeyboardRow();
        for (int i = 1; i < firstDayOfWeek; i++) {
            currentRow.add(" ");
        }

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = currentMonth.atDay(day);
            boolean hasSlots = hasAvailableSlots(user.getSelectedDoctor().getId(), date);

            String buttonText = hasSlots ? "🟢 " + day : "🔴 " + day;
            currentRow.add(buttonText);

            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                rows.add(currentRow);
                currentRow = new KeyboardRow();
            }
        }

        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        keyboard.setKeyboard(rows);

        return SendMessage.builder()
                .chatId(user.getTelegramId().toString())
                .text("Выберите день записи (зеленым отмечены дни со свободными слотами):")
                .replyMarkup(keyboard)
                .build();
    }

    private boolean hasAvailableSlots(Long doctorId, LocalDate date) {
        // Проверяем есть ли расписание на этот день
        List<DoctorSchedule> slots = doctorScheduleRepository.findByDoctorIdAndDate(doctorId, date);
        if (slots.isEmpty()) {
            return false;
        }

        // Проверяем есть ли свободные слоты
        List<Appointment> appointments = appointmentRepository.findByDoctorIdAndDate(doctorId, date);

        return slots.stream().anyMatch(slot ->
                appointments.stream().noneMatch(appt ->
                        appt.getDateTime().toLocalTime().equals(slot.getStartTime())
                )
        );
    }

    private BotApiMethod<?> handleDaySelection(User user, String dayString) {
        try {
            int day = Integer.parseInt(dayString.replaceAll("[^0-9]", ""));
            YearMonth currentMonth = YearMonth.now();
            LocalDate selectedDate = currentMonth.atDay(day);

            if (!hasAvailableSlots(user.getSelectedDoctor().getId(), selectedDate)) {
                return sendMessage(user.getTelegramId(), "На выбранный день нет свободных слотов.");
            }

            user.setSelectedDate(selectedDate);
            user.setAppointmentState(AppointmentState.SELECT_TIME);
            userRepository.save(user);

            return showAvailableTimes(user);
        } catch (Exception e) {
            return sendMessage(user.getTelegramId(), "Неверный формат дня. Выберите день из календаря.");
        }
    }

    private BotApiMethod<?> showAvailableTimes(User user) {
        List<DoctorSchedule> slots = doctorScheduleRepository.findByDoctorIdAndDate(
                user.getSelectedDoctor().getId(),
                user.getSelectedDate()
        );

        List<Appointment> appointments = appointmentRepository.findByDoctorIdAndDate(
                user.getSelectedDoctor().getId(),
                user.getSelectedDate()
        );

        List<String> timeOptions = slots.stream()
                .filter(slot -> appointments.stream().noneMatch(appt ->
                        appt.getDateTime().toLocalTime().equals(slot.getStartTime())))
                .sorted(Comparator.comparing(DoctorSchedule::getStartTime))
                .map(slot -> slot.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) + ", свободно")
                .collect(Collectors.toList());

        if (timeOptions.isEmpty()) {
            return sendMessage(user.getTelegramId(), "На этот день нет свободных слотов. Выберите другой день.");
        }

        // Добавляем кнопку "Назад"
        timeOptions.add("◀ Назад к календарю");

        return SendMessage.builder()
                .chatId(user.getTelegramId().toString())
                .text("Выберите время записи:")
                .replyMarkup(createKeyboard(timeOptions))
                .build();
    }

    private BotApiMethod<?> handleTimeSelection(User user, String timeString) {
        if ("◀ Назад к календарю".equals(timeString)) {
            user.setAppointmentState(AppointmentState.SELECT_DAY);
            userRepository.save(user);
            return showCalendar(user);
        }

        try {
            String timePart = timeString.split(",")[0].trim();
            LocalTime time = LocalTime.parse(timePart, DateTimeFormatter.ofPattern("HH:mm"));

            user.setSelectedTime(time);
            user.setAppointmentState(AppointmentState.ENTER_COMPLAINTS);
            userRepository.save(user);

            return sendMessage(user.getTelegramId(), "Опишите ваши симптомы или жалобы (не обязательно):");
        } catch (Exception e) {
            return sendMessage(user.getTelegramId(), "Неверный формат времени. Выберите время из списка.");
        }
    }

    private BotApiMethod<?> handleComplaintsInput(User user, String text) {
        if (!"◀ Назад к календарю".equals(text)) {
            user.setTemporaryComplaints(text);
        }

        user.setAppointmentState(AppointmentState.CONFIRM_APPOINTMENT);
        userRepository.save(user);

        // Форматируем дату и время
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMMM", new Locale("ru"));
        String formattedDate = user.getSelectedDate().format(dateFormatter);
        String formattedTime = user.getSelectedTime().format(DateTimeFormatter.ofPattern("HH:mm"));

        String confirmationMessage = "Вы собираетесь записаться на " + formattedDate + " " + formattedTime;

        if (user.getTemporaryComplaints() != null && !user.getTemporaryComplaints().isEmpty()) {
            confirmationMessage += "\n\nВаши жалобы:\n" + user.getTemporaryComplaints();
        }

        // Создаем клавиатуру для подтверждения
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        row.add("✅ Записаться");
        row.add("❌ Отменить запись");

        keyboard.setKeyboard(List.of(row));

        return SendMessage.builder()
                .chatId(user.getTelegramId().toString())
                .text(confirmationMessage)
                .replyMarkup(keyboard)
                .build();
    }

    private BotApiMethod<?> handleAppointmentConfirmation(User user, String confirmation) {
        if ("✅ Записаться".equals(confirmation)) {
            // Создаем запись
            Appointment appointment = new Appointment();
            appointment.setPatient(user);
            appointment.setDoctor(user.getSelectedDoctor());
            appointment.setClinic(clinicRepository.findById(user.getSelectedClinicId()).orElse(null));
            appointment.setDateTime(LocalDateTime.of(user.getSelectedDate(), user.getSelectedTime()));
            appointment.setComplaints(user.getTemporaryComplaints());

            appointmentRepository.save(appointment);

            // Сбрасываем состояние
            resetAppointmentState(user);

            // Форматируем дату для сообщения
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM HH:mm", new Locale("ru"));
            String formattedDateTime = appointment.getDateTime().format(formatter);

            // Возвращаем сообщение с клавиатурой главного меню
            return SendMessage.builder()
                    .chatId(user.getTelegramId().toString())
                    .text("✅ Вы успешно записаны на " + formattedDateTime + "!")
                    .replyMarkup(createMainMenuKeyboard(user)) // Добавляем клавиатуру главного меню
                    .build();
        }  else {
            // Отмена записи
            resetAppointmentState(user);
            return SendMessage.builder()
                    .chatId(user.getTelegramId().toString())
                    .text("❌ Запись отменена")
                    .replyMarkup(createMainMenuKeyboard(user)) // Добавляем клавиатуру главного меню
                    .build();
        }
    }

    private void resetAppointmentState(User user) {
        user.setAppointmentState(null);
        user.setSelectedClinicId(null);
        user.setSelectedSpecialization(null);
        user.setSelectedDoctor(null);
        user.setSelectedDate(null);
        user.setSelectedTime(null);
        user.setTemporaryComplaints(null);
        userRepository.save(user);
    }

    // === Реализация загрузки расписания ===

    private BotApiMethod<?> startScheduleUploadProcess(User admin) {
        admin.setScheduleUploadState(ScheduleState.WAITING_MONTH);
        userRepository.save(admin);

        return sendMessage(admin.getTelegramId(),
                "Введите месяц и год для расписания в формате ММ.ГГГГ (например, 06.2025):"
        );
    }

    private BotApiMethod<?> handleScheduleMonthInput(User admin, String monthYear) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM.yyyy");
            YearMonth yearMonth = YearMonth.parse(monthYear, formatter);

            admin.setScheduleMonth(yearMonth.atDay(1));
            admin.setScheduleUploadState(ScheduleState.WAITING_FILE);
            userRepository.save(admin);

            return sendMessage(admin.getTelegramId(),
                    "Теперь отправьте файл Excel с расписанием врачей на " +
                            yearMonth.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru")) + " " +
                            yearMonth.getYear());
        } catch (DateTimeParseException e) {
            return sendMessage(admin.getTelegramId(), "Неверный формат. Введите месяц и год в формате ММ.ГГГГ (например, 06.2025):");
        }
    }

    // Новый метод для обработки состояний загрузки расписания:
    private BotApiMethod<?> handleScheduleUploadState(User admin, Update update) {
        switch (admin.getScheduleUploadState()) {
            case WAITING_MONTH:
                return handleScheduleMonthInput(admin, update.getMessage().getText());
            case WAITING_FILE:
                return handleScheduleFileUpload(admin, update);
            default:
                admin.setScheduleUploadState(null);
                userRepository.save(admin);
                return sendMessage(admin.getTelegramId(), "Произошла ошибка. Попробуйте снова.");
        }
    }

    private BotApiMethod<?> handleScheduleFileUpload(User admin, Update update) {
        if (!update.getMessage().hasDocument()) {
            return sendMessage(admin.getTelegramId(), "Пожалуйста, отправьте файл в формате Excel.");
        }

        Document document = update.getMessage().getDocument();
        String fileName = document.getFileName();

        if (fileName == null || !(fileName.endsWith(".xlsx") || fileName.endsWith(".xls"))) {
            return sendMessage(admin.getTelegramId(), "Поддерживаются только файлы Excel (.xlsx, .xls)");
        }

        try {
            // Скачиваем файл
            java.io.File file = downloadFile(document.getFileId());

            // Парсим расписание
            List<DoctorSchedule> schedules = parseScheduleExcel(file, admin.getScheduleMonth());

            // Проверка результатов парсинга
            if (schedules.isEmpty()) {
                return sendMessage(admin.getTelegramId(),
                        "⚠️ Не найдено валидных записей в файле");
            }

            // Сохраняем расписание
            doctorScheduleRepository.saveAll(schedules);

            // Сбрасываем состояние
            admin.setScheduleUploadState(null);
            admin.setScheduleMonth(null);
            userRepository.save(admin);

            return sendMessage(admin.getTelegramId(), "✅ Расписание успешно загружено! Добавлено " + schedules.size() + " записей.");
        } catch (Exception e) {
            log.error("Ошибка обработки файла расписания", e);
            return sendMessage(admin.getTelegramId(), "Ошибка при обработке файла: " + e.getMessage());
        }
    }

    private List<DoctorSchedule> parseScheduleExcel(File file, LocalDate scheduleMonth) throws Exception {
        List<DoctorSchedule> schedules = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            // Пропускаем заголовок
            if (rowIterator.hasNext()) rowIterator.next();

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                // Парсим данные из строки
                String doctorName = getCellStringValue(row.getCell(0));
                String clinicName = getCellStringValue(row.getCell(1));
                String dateStr = getCellStringValue(row.getCell(2));
                String startTimeStr = getCellStringValue(row.getCell(3));
                String endTimeStr = getCellStringValue(row.getCell(4));
                int slotDuration = (int) row.getCell(5).getNumericCellValue();

                // Парсим дату и время
                LocalDate date = LocalDate.parse(dateStr, dateFormatter);
                LocalTime startTime = LocalTime.parse(startTimeStr, timeFormatter);
                LocalTime endTime = LocalTime.parse(endTimeStr, timeFormatter);

                if (slotDuration < 15) {
                    throw new Exception("Длительность слота должна быть не менее 15 минут");
                }

                // Проверяем соответствие месяцу
                if (!date.getMonth().equals(scheduleMonth.getMonth()) ||
                        date.getYear() != scheduleMonth.getYear()) {
                    throw new Exception("Дата " + date + " не соответствует месяцу расписания " + scheduleMonth.getMonth());
                }

                // Находим врача и клинику
                Optional<User> doctorOpt = userRepository.findByFullName(doctorName);
                Optional<Clinic> clinicOpt = clinicRepository.findByName(clinicName);

                if (doctorOpt.isEmpty()) {
                    throw new Exception("Врач не найден: " + doctorName);
                }
                if (clinicOpt.isEmpty()) {
                    throw new Exception("Клиника не найдена: " + clinicName);
                }

                // Создаем слоты времени
                LocalTime currentTime = startTime;
                while (!currentTime.plusMinutes(slotDuration).isAfter(endTime)) {
                    DoctorSchedule schedule = new DoctorSchedule();
                    schedule.setDoctor(doctorOpt.get());
                    schedule.setClinic(clinicOpt.get());
                    schedule.setDate(date);
                    schedule.setStartTime(currentTime);
                    schedule.setEndTime(currentTime.plusMinutes(slotDuration));
                    schedule.setSlotDuration(slotDuration);

                    schedules.add(schedule);
                    currentTime = currentTime.plusMinutes(slotDuration);
                }
            }
        }

        return schedules;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                } else {
                    return String.valueOf((int) cell.getNumericCellValue());
                }
            default: return "";
        }
    }

    // === Вспомогательные методы ===

    private ReplyKeyboardMarkup createMainMenuKeyboard(User user) {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false); // Меню должно оставаться постоянно

        List<KeyboardRow> rows = new ArrayList<>();

        // Основные кнопки для всех пользователей
        KeyboardRow row1 = new KeyboardRow();
        row1.add("Запись к врачу");
        row1.add("Мои записи");
        rows.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add("Мой профиль");
        rows.add(row2);

        // Кнопки для врачей
        if (user.getRole() == UserRole.DOCTOR) {
            KeyboardRow doctorRow = new KeyboardRow();
            doctorRow.add("Мое расписание");
            doctorRow.add("Записи на сегодня");
            rows.add(doctorRow);
        }

        // Кнопки для администраторов
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.ROOT) {
            KeyboardRow adminRow1 = new KeyboardRow();
            adminRow1.add("Загрузить расписание");
            adminRow1.add("Назначить врача");
            rows.add(adminRow1);

            KeyboardRow adminRow2 = new KeyboardRow();
            adminRow2.add("Управление клиниками");
            adminRow2.add("Управление врачами");
            rows.add(adminRow2);
        }

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    private BotApiMethod<?> showUserAppointments(User user) {
        List<Appointment> appointments = appointmentRepository.findByPatientIdAndDateTimeAfter(
                user.getId(),
                LocalDateTime.now().minusHours(1) // Показываем записи, которые еще не прошли
        );

        if (appointments.isEmpty()) {
            return sendMessage(user.getTelegramId(), "У вас нет активных записей");
        }

        // Отправляем каждую запись отдельным сообщением с кнопкой отмены
        for (Appointment appointment : appointments) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM HH:mm", new Locale("ru"));
            String formattedDateTime = appointment.getDateTime().format(formatter);

            String messageText = String.format(
                    "➤ *%s*\n👨‍⚕️ %s\n🏥 %s\n\n",
                    formattedDateTime,
                    appointment.getDoctor().getFullName(),
                    appointment.getClinic().getName()
            );

            // Создаем inline-кнопку для отмены записи
            InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton cancelButton = new InlineKeyboardButton();
            cancelButton.setText("❌ Отменить запись");
            cancelButton.setCallbackData("cancel_" + appointment.getId());
            row.add(cancelButton);

            rows.add(row);
            inlineKeyboard.setKeyboard(rows);

            SendMessage message = SendMessage.builder()
                    .chatId(user.getTelegramId().toString())
                    .text(messageText)
                    .parseMode("Markdown")
                    .replyMarkup(inlineKeyboard)
                    .build();

            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Error sending appointment message", e);
            }
        }

        return null;
    }

    private BotApiMethod<?> handleCallbackQuery(CallbackQuery callbackQuery) {
        try {
            if (callbackQuery == null || callbackQuery.getMessage() == null) {
                log.error("Invalid callback query: {}", callbackQuery);
                return null;
            }
            String callbackData = callbackQuery.getData();
            Long chatId = callbackQuery.getMessage().getChatId();
            Integer messageId = callbackQuery.getMessage().getMessageId();

            // Обработка отмены записи
            if (callbackData.startsWith("cancel_")) {
                Long appointmentId = Long.parseLong(callbackData.substring(7));
                return cancelAppointment(chatId, messageId, appointmentId);
            }
            else if (callbackData.startsWith("confirm_cancel_")) {
                Long appointmentId = Long.parseLong(callbackData.substring(15));
                return confirmAppointmentCancellation(chatId, messageId, appointmentId);
            }
            else if (callbackData.startsWith("keep_")) {
                // Удаляем сообщение с подтверждением
                return DeleteMessage.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .build();
            }

            return null;
        } catch (Exception e) {
            log.error("Error handling callback", e);
            return SendMessage.builder()
                    .chatId(callbackQuery.getMessage().getChatId().toString())
                    .text("⚠️ Произошла ошибка при обработке запроса")
                    .build();
        }
    }

    private BotApiMethod<?> cancelAppointment(Long chatId, Integer messageId, Long appointmentId) {
        try {
            Optional<Appointment> appointmentOpt = appointmentRepository.findById(appointmentId);
            if (appointmentOpt.isEmpty()) {
                return SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("⚠️ Запись не найдена")
                        .build();
            }

            Appointment appointment = appointmentOpt.get();

            // Проверка что запись принадлежит пользователю
            Optional<User> userOpt = userRepository.findByTelegramId(chatId);
            if (userOpt.isEmpty() || !appointment.getPatient().getId().equals(userOpt.get().getId())) {
                return SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("⚠️ Вы не можете отменить чужую запись")
                        .build();
            }

            // Проверяем, можно ли отменить запись (не позже чем за 24 часа)
            // if (appointment.getDateTime().isBefore(LocalDateTime.now().plusHours(24))) {
            //    return SendMessage.builder()
            //            .chatId(chatId.toString())
            //            .text("⚠️ Отменить запись можно не позднее чем за 24 часа до приема")
            //            .build();
            // }

            InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<InlineKeyboardButton> confirmRow = new ArrayList<>();
            InlineKeyboardButton confirmButton = new InlineKeyboardButton();
            confirmButton.setText("✅ Да, отменить");
            confirmButton.setCallbackData("confirm_cancel_" + appointmentId);
            confirmRow.add(confirmButton);

            List<InlineKeyboardButton> cancelRow = new ArrayList<>();
            InlineKeyboardButton cancelButton = new InlineKeyboardButton();
            cancelButton.setText("❌ Нет, оставить");
            cancelButton.setCallbackData("keep_" + appointmentId);
            cancelRow.add(cancelButton);

            rows.add(confirmRow);
            rows.add(cancelRow);
            inlineKeyboard.setKeyboard(rows);

            return EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .text("❓ Вы уверены, что хотите отменить запись?\n\n" +
                            "После отмены восстановить запись будет невозможно.")
                    .replyMarkup(inlineKeyboard)
                    .build();

            // Удаляем запись
            // appointmentRepository.delete(appointment);

            // Удаляем сообщение с записью
            // DeleteMessage deleteMessage = DeleteMessage.builder()
            //        .chatId(chatId.toString())
            //        .messageId(messageId)
            //        .build();

            // try {
            //    execute(deleteMessage);
            // } catch (TelegramApiException e) {
            //    log.error("Error deleting message", e);
            //}

            //return SendMessage.builder()
            //        .chatId(chatId.toString())
            //        .text("✅ Запись успешно отменена")
            //        .build();
        } catch (Exception e) {
            log.error("Error canceling appointment", e);
            return SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⚠️ Ошибка при отмене записи")
                    .build();
        }

    }

    private BotApiMethod<?> confirmAppointmentCancellation(Long chatId, Integer messageId, Long appointmentId) {
        try {
            Optional<Appointment> appointmentOpt = appointmentRepository.findById(appointmentId);
            if (appointmentOpt.isEmpty()) {
                return SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("⚠️ Запись не найдена")
                        .build();
            }

            // Проверка принадлежности записи
            Optional<User> userOpt = userRepository.findByTelegramId(chatId);
            if (userOpt.isEmpty() || !appointmentOpt.get().getPatient().getId().equals(userOpt.get().getId())) {
                return SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("⚠️ Вы не можете отменить чужую запись")
                        .build();
            }

            // Удаление записи
            appointmentRepository.delete(appointmentOpt.get());

            // Удаляем сообщение с подтверждением
            DeleteMessage deleteMessage = DeleteMessage.builder()
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .build();

            try {
                execute(deleteMessage);
            } catch (TelegramApiException e) {
                log.error("Error deleting message", e);
            }

            return SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("✅ Запись успешно отменена")
                    .build();
        } catch (Exception e) {
            log.error("Error confirming cancellation", e);
            return SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⚠️ Ошибка при подтверждении отмены")
                    .build();
        }
    }

    private BotApiMethod<?> showDoctorPatients(User doctor) {
        List<Appointment> appointments = appointmentRepository.findByDoctorId(doctor.getId());

        if (appointments.isEmpty()) {
            return sendMessage(doctor.getTelegramId(), "У вас нет записей пациентов");
        }

        StringBuilder sb = new StringBuilder("Ваши пациенты:\n\n");
        for (Appointment appointment : appointments) {
            sb.append(String.format("➤ %s %s: %s\n",
                    appointment.getDateTime().toLocalDate(),
                    appointment.getDateTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                    appointment.getPatient().getFullName()
            ));
        }

        return sendMessage(doctor.getTelegramId(), sb.toString());
    }

    private BotApiMethod<?> showTodaysAppointments(User doctor) {
        LocalDate today = LocalDate.now();
        List<Appointment> appointments = appointmentRepository.findByDoctorIdAndDate(
                doctor.getId(),
                today
        );

        if (appointments.isEmpty()) {
            return sendMessage(doctor.getTelegramId(), "На сегодня записей нет");
        }

        StringBuilder sb = new StringBuilder("Записи на сегодня:\n\n");
        for (Appointment appointment : appointments) {
            sb.append(String.format("➤ %s: %s\n",
                    appointment.getDateTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                    appointment.getPatient().getFullName()
            ));
        }

        return sendMessage(doctor.getTelegramId(), sb.toString());
    }

    private BotApiMethod<?> showDoctorSchedule(User doctor) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusWeeks(2);

        List<DoctorSchedule> schedules = doctorScheduleRepository.findByDoctorIdAndDateBetween(
                doctor.getId(), startDate, endDate
        );

        if (schedules.isEmpty()) {
            return sendMessage(doctor.getTelegramId(), "Расписание на ближайшие 2 недели не найдено");
        }

        Map<LocalDate, List<DoctorSchedule>> scheduleByDate = schedules.stream()
                .collect(Collectors.groupingBy(DoctorSchedule::getDate));

        StringBuilder sb = new StringBuilder("Ваше расписание:\n\n");
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (scheduleByDate.containsKey(date)) {
                sb.append("📅 ").append(date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append("\n");

                for (DoctorSchedule slot : scheduleByDate.get(date)) {
                    sb.append("⏱ ").append(slot.getStartTime()).append(" - ").append(slot.getEndTime()).append("\n");
                }

                sb.append("\n");
            }
        }

        return sendMessage(doctor.getTelegramId(), sb.toString());
    }

    private BotApiMethod<?> manageClinics(User admin) {
        // Временная реализация вместо заглушки
        admin.setAppointmentState(AppointmentState.MANAGE_CLINICS);
        userRepository.save(admin);

        List<Clinic> clinics = clinicRepository.findAll();
        List<String> options = clinics.stream()
                .map(c -> c.getName() + " (ID: " + c.getId() + ")")
                .collect(Collectors.toList());

        options.add("➕ Добавить клинику");
        options.add("❌ Удалить клинику");
        options.add("🔙 Назад");

        return createSelectionMenu(
                admin.getTelegramId(),
                "Управление клиниками:",
                options
        );
    }
    private BotApiMethod<?> handleClinicManagement(User admin, String text) {
        if (text.equals("➕ Добавить клинику")) {
            admin.setAppointmentState(AppointmentState.MANAGE_CLINICS);
            userRepository.save(admin);
            return sendMessage(admin.getTelegramId(), "Введите название новой клиники:");
        }
        // ... другие действия
        return null;
    }

    private BotApiMethod<?> manageDoctors(User admin) {
        admin.setAppointmentState(AppointmentState.MANAGE_DOCTORS);
        userRepository.save(admin);

        List<User> doctors = userRepository.findByRole(UserRole.DOCTOR);
        List<String> options = doctors.stream()
                .map(User::getFullName) // Используем только ФИО
                .collect(Collectors.toList());

        options.add("➕ Добавить врача");
        options.add("✏️ Редактировать врача");
        options.add("❌ Удалить врача");
        options.add("🔙 Назад");

        return createSelectionMenu(
                admin.getTelegramId(),
                "Управление врачами:",
                options
        );
    }

    private BotApiMethod<?> handleDoctorManagement(User admin, String text) {
        if (text.equals("➕ Добавить врача")) {
            admin.setAppointmentState(AppointmentState.ADD_DOCTOR);
            userRepository.save(admin);
            return sendMessage(admin.getTelegramId(), "Введите ФИО нового врача:");
        }
        else if (text.equals("✏️ Редактировать врача")) {
            admin.setAppointmentState(AppointmentState.EDIT_DOCTOR);
            userRepository.save(admin);
            return showDoctorsForEditing(admin);
        }
        else if (text.equals("❌ Удалить врача")) {
            admin.setAppointmentState(AppointmentState.DELETE_DOCTOR);
            userRepository.save(admin);
            return showDoctorsForDeletion(admin);
        }
        else if (text.equals("🔙 Назад")) {
            resetAppointmentState(admin);
            return handleAdminCommands(admin, "Управление врачами");
        }

        // Просмотр информации о враче
        return sendMessage(admin.getTelegramId(), "Информация о враче:\n" + text);
    }

    private BotApiMethod<?> handleAddDoctor(User admin, String text) {
        if (admin.getSelectedDoctor() == null) {
            // Сохраняем имя врача
            User newDoctor = new User();
            newDoctor.setFullName(text);
            newDoctor.setRole(UserRole.DOCTOR);
            admin.setSelectedDoctor(newDoctor);
            userRepository.save(admin);

            return createSelectionMenu(
                    admin.getTelegramId(),
                    "Выберите специализацию врача:",
                    Arrays.asList("Терапевт", "Хирург", "Кардиолог", "Невролог", "Офтальмолог", "Отоларинголог", "Педиатр")
            );
        }
        else if (admin.getSelectedDoctor().getSpecialization() == null) {
            // Сохраняем специализацию
            admin.getSelectedDoctor().setSpecialization(text);
            userRepository.save(admin);

            List<Clinic> clinics = clinicRepository.findAll();
            return createSelectionMenu(
                    admin.getTelegramId(),
                    "Выберите клиники для врача:",
                    clinics.stream().map(Clinic::getName).collect(Collectors.toList())
            );
        }
        else {
            // Обработка клиник
            if (text.equals("✅ Завершить")) {
                // Сохраняем врача
                User doctor = admin.getSelectedDoctor();
                userRepository.save(doctor);

                resetAppointmentState(admin);
                return sendMessage(admin.getTelegramId(),
                        "✅ Врач " + doctor.getFullName() + " добавлен!\n" +
                                "Клиники: " + doctor.getClinics().stream()
                                .map(Clinic::getName)
                                .collect(Collectors.joining(", "))
                );
            }
            else if (text.equals("◀ Назад")) {
                // Вернуться к выбору специализации
                admin.getSelectedDoctor().setSpecialization(null);
                userRepository.save(admin);

                return createSelectionMenu(
                        admin.getTelegramId(),
                        "Выберите специализацию врача:",
                        Arrays.asList("Терапевт", "Хирург", "Кардиолог", "Невролог",
                                "Офтальмолог", "Отоларинголог", "Педиатр")
                );
            }
            else {
                // Добавление/удаление клиники
                Optional<Clinic> clinicOpt = clinicRepository.findByName(text);
                if (clinicOpt.isPresent()) {
                    Clinic clinic = clinicOpt.get();
                    User doctor = admin.getSelectedDoctor();

                    if (doctor.getClinics().contains(clinic)) {
                        doctor.getClinics().remove(clinic);
                    } else {
                        doctor.getClinics().add(clinic);
                    }

                    userRepository.save(admin);

                    // Обновляем список клиник
                    List<String> clinicOptions = clinicRepository.findAll().stream()
                            .map(c -> (doctor.getClinics().contains(c) ? "✅ " : "") + c.getName())
                            .collect(Collectors.toList());

                    clinicOptions.add("✅ Завершить");
                    clinicOptions.add("◀ Назад");

                    return createSelectionMenu(
                            admin.getTelegramId(),
                            "Выберите клиники для врача (отмечены текущие):",
                            clinicOptions
                    );
                }
            }
        }
        return null;
    }

    private BotApiMethod<?> showDoctorsForEditing(User admin) {
        List<User> doctors = userRepository.findByRole(UserRole.DOCTOR);
        List<String> options = new ArrayList<>();

        // Формируем сообщение с детальной информацией
        StringBuilder message = new StringBuilder("Выберите врача для редактирования:\n\n");

        for (User doctor : doctors) {
            String clinics = doctor.getClinics().stream()
                    .map(Clinic::getName)
                    .collect(Collectors.joining(", "));

            message.append("👨‍⚕️ ")
                    .append(doctor.getFullName())
                    .append(" (")
                    .append(doctor.getSpecialization())
                    .append(")\n🏥 Клиники: ")
                    .append(clinics)
                    .append("\n\n");

            // В кнопки добавляем только ФИО врача
            options.add(doctor.getFullName());
        }

        options.add("🔙 Назад");

        return SendMessage.builder()
                .chatId(admin.getTelegramId().toString())
                .text(message.toString())
                .replyMarkup(createKeyboard(options))
                .build();
    }

    private BotApiMethod<?> handleEditDoctor(User admin, String text) {
        if (text.equals("🔙 Назад")) {
            return manageDoctors(admin);
        }

        // Выбор действия для уже выбранного врача
        if (admin.getSelectedDoctor() != null) {
            return handleEditDoctorAction(admin, text);
        }

        // Выбор врача по имени
        Optional<User> doctorOpt = userRepository.findByFullName(text);
        if (doctorOpt.isEmpty()) {
            return sendMessage(admin.getTelegramId(), "Врач не найден");
        }

        admin.setSelectedDoctor(doctorOpt.get());
        userRepository.save(admin);
        return showEditOptions(admin);
    }

    private BotApiMethod<?> showEditOptions(User admin) {
        User doctor = admin.getSelectedDoctor();
        List<String> options = Arrays.asList(
                "✏️ Изменить ФИО",
                "⚕️ Изменить специализацию",
                "🏥 Изменить клиники",
                "🔙 Назад"
        );

        String clinics = doctor.getClinics().stream()
                .map(Clinic::getName)
                .collect(Collectors.joining(", "));

        String message = "Текущие данные врача:\n\n" +
                "👨‍⚕️ ФИО: " + doctor.getFullName() + "\n" +
                "⚕️ Специализация: " + doctor.getSpecialization() + "\n" +
                "🏥 Клиники: " + clinics + "\n\n" +
                "Что вы хотите изменить?";

        return SendMessage.builder()
                .chatId(admin.getTelegramId().toString())
                .text(message)
                .replyMarkup(createKeyboard(options))
                .build();
    }

    private BotApiMethod<?> handleEditDoctorAction(User admin, String action) {
        switch (action) {
            case "✏️ Изменить ФИО":
                admin.setAppointmentState(AppointmentState.EDIT_DOCTOR_FULLNAME);
                userRepository.save(admin);
                return sendMessage(admin.getTelegramId(), "Введите новое ФИО врача:");

            case "⚕️ Изменить специализацию":
                admin.setAppointmentState(AppointmentState.EDIT_DOCTOR_SPECIALIZATION);
                userRepository.save(admin);
                return createSelectionMenu(
                        admin.getTelegramId(),
                        "Выберите специализацию:",
                        Arrays.asList("Терапевт", "Хирург", "Кардиолог", "Невролог")
                );

            case "🏥 Изменить клиники":
                admin.setAppointmentState(AppointmentState.EDIT_DOCTOR_CLINICS);
                userRepository.save(admin);
                return showClinicSelectionForDoctor(admin);

            case "🔙 Назад":
                admin.setSelectedDoctor(null);
                userRepository.save(admin);
                return showDoctorsForEditing(admin);

            default:
                return sendMessage(admin.getTelegramId(), "Неизвестное действие");
        }
    }

    private BotApiMethod<?> showClinicSelectionForDoctor(User admin) {
        try {
            User doctor = userRepository.findById(admin.getSelectedDoctor().getId()).orElse(null);
            if (doctor == null) {
                return sendMessage(admin.getTelegramId(), "⚠️ Врач не найден");
            }

            List<Clinic> allClinics = clinicRepository.findAll();
            List<String> clinicOptions = new ArrayList<>();

            for (Clinic clinic : allClinics) {
                boolean isSelected = doctor.getClinics().contains(clinic);
                clinicOptions.add((isSelected ? "✅ " : "") + clinic.getName());
            }

            clinicOptions.add("✅ Завершить");
            clinicOptions.add("◀ Назад");

            return createSelectionMenu(
                    admin.getTelegramId(),
                    "Выберите клиники для врача (отмечены текущие):",
                    clinicOptions
            );
        } catch (Exception e) {
            log.error("Error loading doctor clinics", e);
            return sendMessage(admin.getTelegramId(), "⚠️ Ошибка загрузки клиник");
        }
    }


    private BotApiMethod<?> handleEditDoctorFullName(User admin, String newFullName) {
        User doctor = admin.getSelectedDoctor();
        doctor.setFullName(newFullName);
        userRepository.save(doctor);

        resetAppointmentState(admin);
        return SendMessage.builder()
                .chatId(admin.getTelegramId().toString())
                .text("✅ ФИО врача изменено на: " + newFullName)
                .replyMarkup(createMainMenuKeyboard(admin))
                .build();
    }

    private BotApiMethod<?> handleEditDoctorSpecialization(User admin, String specialization) {
        User doctor = admin.getSelectedDoctor();
        doctor.setSpecialization(specialization);
        userRepository.save(doctor);

        resetAppointmentState(admin);
        return SendMessage.builder()
                .chatId(admin.getTelegramId().toString())
                .text("✅ Специализация врача изменена на: " + specialization)
                .replyMarkup(createMainMenuKeyboard(admin))
                .build();
    }

    private BotApiMethod<?> handleEditDoctorClinics(User admin, String clinicName) {
        if ("✅ Завершить".equals(clinicName)) {
            userRepository.save(admin.getSelectedDoctor());
            resetAppointmentState(admin);
            return SendMessage.builder()
                    .chatId(admin.getTelegramId().toString())
                    .text("✅ Клиники врача успешно обновлены!")
                    .replyMarkup(createMainMenuKeyboard(admin))
                    .build();
        } else if ("◀ Назад".equals(clinicName)) {
            return showEditOptions(admin);
        }

        try {
            String pureClinicName = clinicName.replace("✅ ", "").trim();
            Optional<Clinic> clinicOpt = clinicRepository.findByName(pureClinicName);

            if (clinicOpt.isEmpty()) {
                return sendMessage(admin.getTelegramId(), "Клиника не найдена: " + pureClinicName);
            }

            Clinic clinic = clinicOpt.get();
            User doctor = admin.getSelectedDoctor();

            // Создаем новую коллекцию на основе текущего списка клиник
            List<Clinic> updatedClinics = new ArrayList<>(doctor.getClinics());

            if (updatedClinics.contains(clinic)) {
                updatedClinics.remove(clinic);
            } else {
                updatedClinics.add(clinic);
            }

            // Обновляем список клиник
            doctor.setClinics(updatedClinics);
            userRepository.save(doctor);

            return showClinicSelectionForDoctor(admin);
        } catch (Exception e) {
            log.error("Error updating doctor clinics", e);
            return sendMessage(admin.getTelegramId(), "⚠️ Ошибка обновления клиник");
        }
    }

    private BotApiMethod<?> showDoctorsForDeletion(User admin) {
        List<User> doctors = userRepository.findByRole(UserRole.DOCTOR);
        List<String> options = doctors.stream()
                .map(d -> d.getFullName() + " (" + d.getSpecialization() + ")")
                .collect(Collectors.toList());

        options.add("🔙 Назад");

        return createSelectionMenu(
                admin.getTelegramId(),
                "Выберите врача для удаления:",
                options
        );
    }

    private BotApiMethod<?> handleDeleteDoctor(User admin, String text) {
        if (text.equals("🔙 Назад")) {
            return manageDoctors(admin);
        }

        String doctorName = text.split("\\(")[0].trim();
        Optional<User> doctorOpt = userRepository.findByFullName(doctorName);

        if (doctorOpt.isEmpty()) {
            return sendMessage(admin.getTelegramId(), "Врач не найден");
        }

        User doctor = doctorOpt.get();
        Long doctorId = doctor.getId();

        // Проверка активных записей
        List<Appointment> futureAppointments = appointmentRepository.findByDoctorIdAndDateTimeAfter(
                doctorId,
                LocalDateTime.now()
        );

        if (!futureAppointments.isEmpty()) {
            return sendMessage(admin.getTelegramId(),
                    "❌ Нельзя удалить врача с активными записями!\n" +
                            "У врача есть " + futureAppointments.size() + " предстоящих приемов.");
        }

        // Если записей нет - удаляем врача
        userRepository.delete(doctor);
        resetAppointmentState(admin);
        return sendMessage(admin.getTelegramId(), "✅ Врач " + doctorName + " удалён");
    }

    private BotApiMethod<?> handleDoctorSpecializationSelection(User doctor) {
        List<String> specializations = Arrays.asList(
                "Терапевт", "Хирург", "Кардиолог", "Невролог",
                "Офтальмолог", "Отоларинголог", "Педиатр"
        );

        return createSelectionMenu(
                doctor.getTelegramId(),
                "Выберите вашу специальность:",
                specializations
        );
    }

    private BotApiMethod<?> showUserProfile(User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("👤 Ваш профиль\n\n");
        sb.append("▸ ФИО: ").append(user.getFullName()).append("\n");
        sb.append("▸ Телефон: ").append(user.getPhone()).append("\n");
        sb.append("▸ Роль: ").append(getRoleDisplay(user.getRole())).append("\n");

        if (user.getRole() == UserRole.DOCTOR) {
            sb.append("▸ Специализация: ").append(
                    user.getSpecialization() != null ? user.getSpecialization() : "не указана"
            ).append("\n");
        }

        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.ROOT) {
            sb.append("▸ Состояние: ").append(
                    user.getAdminState() != null ? user.getAdminState() : "нет активных действий"
            ).append("\n");
        }

        return sendMessage(user.getTelegramId(), sb.toString());
    }

    private String getRoleDisplay(String role) {
        switch (role) {
            case "PATIENT": return "Пациент";
            case "DOCTOR": return "Врач";
            case "ADMIN": return "Администратор";
            case "ROOT": return "Главный администратор";
            default: return role;
        }
    }

    private BotApiMethod<?> createSelectionMenu(Long chatId, String message, List<String> options) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(message)
                .replyMarkup(createKeyboard(options))
                .build();
    }

    private ReplyKeyboardMarkup createKeyboard(List<String> options) {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow currentRow = new KeyboardRow();

        for (String option : options) {
            if (currentRow.size() >= 2) { // Максимум 2 кнопки в строке
                rows.add(currentRow);
                currentRow = new KeyboardRow();
            }
            currentRow.add(option);
        }

        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        // Добавляем кнопку "Назад"
        KeyboardRow backRow = new KeyboardRow();
        backRow.add("🔙 Назад");
        rows.add(backRow);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    private ReplyKeyboardMarkup createPhoneRequestKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        KeyboardButton phoneButton = new KeyboardButton("Отправить номер");
        phoneButton.setRequestContact(true);
        row.add(phoneButton);

        keyboard.setKeyboard(List.of(row));
        return keyboard;
    }

    private SendMessage sendMessage(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build();
    }

    private SendMessage sendMessage(Long chatId, String text, ReplyKeyboardMarkup keyboard) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(keyboard)
                .build();
    }

    private String getRoleDisplay(UserRole role) {
        if (role == null) return "Не определена";
        switch (role) {
            case PATIENT: return "Пациент";
            case DOCTOR: return "Врач";
            case ADMIN: return "Администратор";
            case ROOT: return "Главный администратор";
            default: return role.name();
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotPath() {
        return botPath;
    }
}