package com.clinicbot.clinic_bot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
@RequestMapping("/api")
public class WebhookController {
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private final ClinicTelegramBot bot;

    @Autowired
    public WebhookController(ClinicTelegramBot bot) {
        this.bot = bot;
    }

    @PostMapping("/webhook")
    public ResponseEntity<BotApiMethod<?>> handleWebhook(@RequestBody Update update) {
        try {
            // Определяем chatId в зависимости от типа обновления
            Long chatId = null;
            if (update.hasCallbackQuery()) {
                chatId = update.getCallbackQuery().getMessage().getChatId();
                log.info("Received CALLBACK update for chatId: {}", chatId);
            } else if (update.hasMessage()) {
                chatId = update.getMessage().getChatId();
                log.info("Received MESSAGE update for chatId: {}", chatId);
            } else {
                log.warn("Received unsupported update type: {}", update);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Обработка обновления
            BotApiMethod<?> response = bot.onWebhookUpdateReceived(update);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing update", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}


