package com.forty2apps;

import java.io.IOException;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.tika.exception.TikaException;
import org.telegram.telegrambots.api.methods.send.SendDocument;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.xml.sax.SAXException;

class WebTextBot extends TelegramLongPollingBot {

  private final UrlValidator urlValidator = new UrlValidator();
  private final WebTextExtractor webTextExtractor = new WebTextExtractor();
  private static final Logger LOGGER = Logger.getLogger("WebTextBot");
  private final BotConfig botConfig;

  WebTextBot(BotConfig botConfig) {
    this.botConfig = botConfig;
  }

  public void onUpdateReceived(Update update) {
    LOGGER.info("Received update: " + update);
    final Long chatId = update.getMessage().getChatId();
    if (update.hasMessage() && update.getMessage().hasText()) {
      if (urlValidator.isValid(update.getMessage().getText())) {
        sendTextMessage(chatId, "Working on it");
        try {
          final String article = webTextExtractor.parseToPlainText(update.getMessage().getText());
          boolean sent = sendTextMessage(chatId, article);
          if (!sent) {
            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(chatId);
            sendDocument.setNewDocument("webPage.txt", IOUtils.toInputStream(article, "UTF-8"));
            sendDocument(sendDocument);
          }
        } catch (TelegramApiException | IOException | TikaException | SAXException e) {
          e.printStackTrace();
          sendTextMessage(chatId, "An error occurred while serving your request");

        }
      } else if (update.getMessage().getText().toLowerCase().startsWith("/start")) {
        sendTextMessage(chatId, "BOT started, send me a web address");
      } else {
        sendTextMessage(chatId,
            "Sorry, I don't know what you're talking about. Send me an URL maybe?");
      }
    }
  }

  private boolean sendTextMessage(Long chatId, String message) {
    SendMessage sendMessage = new SendMessage();
    sendMessage.setChatId(chatId.toString());
    sendMessage.setText(message);
    try {
      execute(sendMessage);
    } catch (TelegramApiException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public String getBotUsername() {
    return botConfig.username();
  }

  public String getBotToken() {
    return botConfig.token();
  }
}
