package com.clinicbot.clinic_bot.service;


import com.clinicbot.clinic_bot.model.User;
// import com.clinicbot.clinic_bot.model.RegistrationStep;
// import com.clinicbot.clinic_bot.model.UserStatus;
import com.clinicbot.clinic_bot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ClinicTelegramBot extends TelegramWebhookBot {
    private final UserRepository userRepository;

    @Value("${bot.token}")
    private String botToken;

    @Value("${bot.username}")
    private String botUsername;

    @Value("${bot.path}")
    private String botPath;

    public ClinicTelegramBot(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        if (!update.hasMessage()) return null;

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().hasText() ? update.getMessage().getText() : "";

        // Обработка /start
        if (text.equals("/start")) {
            return handleStartCommand(chatId);
        }

        // Обработка этапов регистрации
        Optional<User> userOpt = userRepository.findByTelegramId(chatId);
        if (userOpt.isPresent() && userOpt.get().getRegistrationStep() != RegistrationStep.COMPLETED) {
            return handleRegistrationStep(userOpt.get(), text, update);
        }
        return null;
    }

    private BotApiMethod<?> handleStartCommand(Long chatId) {
        Optional<User> userOpt = userRepository.findByTelegramId(chatId);
        SendMessage response = new SendMessage();
        response.setChatId(chatId.toString());

        if (userOpt.isEmpty()) {
            // Создать нового пользователя
            User newUser = new User();
            newUser.setTelegramId(chatId);
            newUser.setRegistrationStep(RegistrationStep.ENTER_PHONE);
            newUser.setStatus(UserStatus.PENDING);
            userRepository.save(newUser);

            response.setText("Для регистрации нажмите кнопку «Отправить номер»:");
            response.setReplyMarkup(createPhoneRequestKeyboard());
        } else {
            User user = userOpt.get();
            // Проверяем, завершена ли регистрация
            if (user.getRegistrationStep() == RegistrationStep.COMPLETED) {
                response.setText("Вы уже зарегистрированы! Выберите действие:");
                response.setReplyMarkup(createMainMenuKeyboard());
            } else {
                // Продолжаем регистрацию с текущего шага
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

    private ReplyKeyboardMarkup createMainMenuKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        row.add("Запись к врачу");
        row.add("Мои записи");

        keyboard.setKeyboard(List.of(row));
        return keyboard;
    }

    private BotApiMethod<?> handleRegistrationStep(User user, String text, Update update) {
        SendMessage response = new SendMessage();
        response.setChatId(user.getTelegramId().toString());

        switch (user.getRegistrationStep()) {
            case ENTER_PHONE:
                if (update.getMessage().hasContact()) {
                    user.setPhone(update.getMessage().getContact().getPhoneNumber());
                    user.setRegistrationStep(RegistrationStep.ENTER_FULL_NAME);
                    response.setText("Введите ваше полное имя (ФИО):");
                }
                break;

            case ENTER_FULL_NAME:
                user.setFullName(text);
                user.setRegistrationStep(RegistrationStep.COMPLETED);
                response.setText("Регистрация завершена! Выберите действие:");
                response.setReplyMarkup(createMainMenuKeyboard());
                break;
        }

        userRepository.save(user);
        return response;
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
        return "/webhook";
    }
}
