package com.clinicbot.clinic_bot.config;


import com.clinicbot.clinic_bot.service.ClinicTelegramBot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class BotConfig {

    @Value("${bot.webhook-url}") // Используйте значение из application.properties
    private String webhookUrl;

    @Bean
    public SetWebhook setWebhookInstance() {
        SetWebhook webhook = new SetWebhook();
        webhook.setUrl(webhookUrl + "/webhook");
        return webhook;
    }
}
