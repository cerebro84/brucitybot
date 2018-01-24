package com.forty2apps;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.commons.validator.routines.UrlValidator;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

class BruCityBot extends TelegramLongPollingBot {

    private final UrlValidator urlValidator = new UrlValidator();
    private final MoreRecentRDVChecker moreRecentRDVChecker = new MoreRecentRDVChecker();
    private static final Logger LOGGER = Logger.getLogger("BruCityBot");
    private final BotConfig botConfig;
    private final HashMap<Long, Map<String, String>> cache;

    BruCityBot(BotConfig botConfig) {
        this.botConfig = botConfig;
        this.cache = new HashMap<Long, Map<String, String>>();
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(() -> {
            cache.forEach((key, value) -> {
                try {
                    moreRecentRDVChecker.parseToPlainText(cache.get(key).get("url"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }, 0, 5, TimeUnit.SECONDS);
    }

    public void onUpdateReceived(Update update) {
        LOGGER.info("Received update: " + update);
        final Long chatId = update.getMessage().getChatId();
        if (update.hasMessage() && update.getMessage().hasText()) {
            final String message = update.getMessage().getText();
            if (urlValidator.isValid(message)) {
                sendTextMessage(chatId, "Working on it");
                cache.computeIfAbsent(chatId, k -> new HashMap<>()).put("url", message);

            } else if (message.toLowerCase().startsWith("/start")) {
                sendTextMessage(chatId, "BOT started, send me a web address");
            } else {
                sendTextMessage(chatId,
                        "Sorry, I don't know what you're talking about. Send me an URL maybe?");
            }
        }
    }

    private void sendTextMessage(Long chatId, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(message);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public String getBotUsername() {
        return botConfig.username();
    }

    public String getBotToken() {
        return botConfig.token();
    }
}
