package forty2apps

import org.cfg4j.provider.ConfigurationProviderBuilder
import org.cfg4j.source.classpath.ClasspathConfigurationSource
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.exceptions.TelegramApiException
import java.nio.file.Path
import java.nio.file.Paths

internal object App {

    private val botConfig: BotConfig
        get() {
            val configFilesProvider = { listOf<Path>(Paths.get("application.yaml")) }
            val source = ClasspathConfigurationSource(configFilesProvider)

            val configurationProvider = ConfigurationProviderBuilder()
                    .withConfigurationSource(source)
                    .build()

            return configurationProvider.bind("telegram", BotConfig::class.java)
        }

    @JvmStatic
    fun main(args: Array<String>) {
        ApiContextInitializer.init()
        val telegramBotsApi = TelegramBotsApi()

        try {
            telegramBotsApi.registerBot(BruCityBot(botConfig))
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }

    }
}
