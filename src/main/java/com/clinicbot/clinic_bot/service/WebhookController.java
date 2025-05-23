package com.clinicbot.clinic_bot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
public class WebhookController {
    private final ClinicTelegramBot bot;

    @Autowired
    public WebhookController(ClinicTelegramBot bot) {
        this.bot = bot;
    }

    @PostMapping("/webhook")
    public BotApiMethod<?> handleWebhook(@RequestBody Update update) {
        return bot.onWebhookUpdateReceived(update);
    }
}

