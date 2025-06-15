package com.clinicbot.clinic_bot.config;

import com.clinicbot.clinic_bot.service.ClinicTelegramBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.annotation.PostConstruct;

@Component
public class BotInitializer {
    private static final Logger log = LoggerFactory.getLogger(BotInitializer.class);

    private final ClinicTelegramBot bot;
    private final TelegramBotsApi botsApi;
    private final String webhookUrl;
    private final String botPath;

    public BotInitializer(ClinicTelegramBot bot,
                          TelegramBotsApi botsApi,
                          @Value("${bot.webhook-url}") String webhookUrl,
                          @Value("${bot.path}") String botPath) {
        this.bot = bot;
        this.botsApi = botsApi;
        this.webhookUrl = webhookUrl;
        this.botPath = botPath;
    }

    @PostConstruct
    public void init() {
        try {
            String fullUrl = webhookUrl + botPath;
            log.info("Initializing bot with webhook: {}", fullUrl);

            SetWebhook webhook = SetWebhook.builder()
                    .url(fullUrl)
                    .build();

            botsApi.registerBot(bot, webhook);
            log.info("Bot initialized successfully");
        } catch (TelegramApiException e) {
            log.error("Bot initialization failed", e);
            // Повторная попытка через 10 секунд
            new Thread(() -> {
                try {
                    Thread.sleep(10000);
                    init();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
}