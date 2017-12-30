package com.forty2apps;

import java.nio.file.Paths;
import java.util.Collections;
import org.cfg4j.provider.ConfigurationProvider;
import org.cfg4j.provider.ConfigurationProviderBuilder;
import org.cfg4j.source.ConfigurationSource;
import org.cfg4j.source.classpath.ClasspathConfigurationSource;
import org.cfg4j.source.context.filesprovider.ConfigFilesProvider;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.exceptions.TelegramApiException;

class App {

  public static void main(String... args) {
    ApiContextInitializer.init();
    TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
    try {
      telegramBotsApi.registerBot(new WebTextBot(getBotConfig()));
    } catch (TelegramApiException e) {
      e.printStackTrace();
    }

  }

  private static BotConfig getBotConfig() {
    ConfigFilesProvider configFilesProvider = () -> Collections
        .singletonList(Paths.get("application.yaml"));
    ConfigurationSource source = new ClasspathConfigurationSource(configFilesProvider);

    final ConfigurationProvider configurationProvider = new ConfigurationProviderBuilder()
        .withConfigurationSource(source)
        .build();

    return configurationProvider.bind("telegram", BotConfig.class);
  }
}
