package forty2apps

import org.apache.commons.validator.routines.UrlValidator
import org.apache.logging.log4j.LogManager
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.exceptions.TelegramApiException
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class BruCityBot(private val botConfig: BotConfig) : TelegramLongPollingBot() {
    private val urlValidator = UrlValidator()
    private val moreRecentRDVChecker = MoreRecentRDVChecker()
    private val cache: MutableMap<Long, MutableMap<String, String>> = mutableMapOf()

    init {
        readCache()
        val exec = Executors.newSingleThreadScheduledExecutor()
        exec.scheduleWithFixedDelay({
            cache.forEach { chatId, _ ->
                LOGGER.info("Checking for new date")
                checkForNewDate(chatId)
            }
        }, 0, 120, TimeUnit.SECONDS)
    }

    private fun checkForNewDate(key: Long?) {
        val executor = Executors.newSingleThreadExecutor()
        val stuffToDo = fun() {
            var lastExit: String
            val cacheForChat = cache[key] ?: return

            try {
                cacheForChat[CACHE_KEY_LAST_CHECK] = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
                val rendezVousInfo = moreRecentRDVChecker.maybeGetNewDate(cacheForChat["url"]!!)
                lastExit = if (rendezVousInfo.newDateIsBetter()) {
                    "New date found:" + rendezVousInfo.newPossibleDate
                } else {
                    "New date was in the future: " + rendezVousInfo.newPossibleDate
                }
                cacheForChat["newDate"] = rendezVousInfo.newPossibleDate.toString()
                cacheForChat["rdvDate"] = rendezVousInfo.rdvDate.toString()
                if (rendezVousInfo.newDateIsBetter()) {
                    sendTextMessage(key!!, String
                            .format("Data migliore disponibile: %s; data precedente: %s", rendezVousInfo.newPossibleDate,
                                    rendezVousInfo.rdvDate))
                }
            } catch (e: IOException) {
                lastExit = "Failure: " + e.message
                LOGGER.error("Error occurred", e)
            }

            cacheForChat["lastExit"] = lastExit

        }
        val future = executor.submit(stuffToDo)
        try {
            future.get(10, TimeUnit.SECONDS)
        } catch (ee: ExecutionException) {
            LOGGER.error("Execution error:", ee)
        } catch (ee: InterruptedException) {
            LOGGER.error("Execution error:", ee)
        } catch (te: TimeoutException) {
            LOGGER.info("Operation timed out")
        }

        if (!executor.isTerminated) {
            executor.shutdownNow()
        }

    }

    override fun onUpdateReceived(update: Update) {
        LOGGER.info("Received update: {}", update)
        val chatId = update.message.chatId
        if (update.hasMessage() && update.message.hasText()) {
            val message = update.message.text
            when {
                urlValidator.isValid(message) -> {
                    sendTextMessage(chatId!!, "Working on it")
                    cache.computeIfAbsent(chatId) { _ -> mutableMapOf() }["url"] = message
                    saveCache()
                    checkForNewDate(chatId)
                }
                message.toLowerCase().startsWith("/start") -> sendTextMessage(chatId!!, "BOT started, send me a web address")
                else -> {
                    val currentCache: Map<String, String> = cache.getOrDefault(chatId, emptyMap())
                    when {
                        message.toLowerCase().startsWith("/lastcheck") ->
                            sendTextMessage(chatId!!, "Last check: " + (currentCache as MutableMap<String, String>).getOrDefault(CACHE_KEY_LAST_CHECK, ""))
                        message.toLowerCase().startsWith("/lastexit") -> sendTextMessage(chatId!!, "Last exit: " + (currentCache as MutableMap<String, String>)
                                .getOrDefault("lastExit", ""))
                        message.toLowerCase().startsWith("/rdvinfo") -> sendTextMessage(chatId!!, String.format("Last check: %s;%nnext date: %s;%ncurrent rdv: %s",
                                currentCache.getOrDefault(CACHE_KEY_LAST_CHECK, ""),
                                currentCache.getOrDefault("newDate", ""),
                                currentCache.getOrDefault("rdvDate", "")))
                        else -> sendTextMessage(chatId!!,
                                "Sorry, I don't know what you're talking about. Send me an URL maybe?")
                    }
                }
            }
        }
    }

    private fun readCache() {
        try {
            @Suppress("UNCHECKED_CAST")
            ObjectInputStream(FileInputStream(botConfig.cacheFile())).use { stream -> cache.putAll(stream.readObject() as Map<Long, MutableMap<String, String>>) }
        } catch (e: IOException) {
            LOGGER.error("Error while reading cache:", e)
        } catch (e: ClassNotFoundException) {
            LOGGER.error("Error while reading cache:", e)
        }

    }

    private fun saveCache() {
        try {
            ObjectOutputStream(FileOutputStream(botConfig.cacheFile())).use { stream ->
                stream.writeObject(cache)
                LOGGER.info("Cache saved")
            }
        } catch (e: IOException) {
            LOGGER.error("Error while saving cache:", e)
        }

    }

    private fun sendTextMessage(chatId: Long, message: String) {
        val sendMessage = SendMessage()
        sendMessage.chatId = chatId.toString()
        sendMessage.text = message
        try {
            execute<Message, SendMessage>(sendMessage)
        } catch (e: TelegramApiException) {
            LOGGER.error("Error occurred", e)
        }

    }

    override fun getBotUsername(): String {
        return botConfig.username()
    }

    override fun getBotToken(): String {
        return botConfig.token()
    }

    companion object {

        private const val CACHE_KEY_LAST_CHECK = "lastCheck"
        private val LOGGER = LogManager.getLogger("BruCityBot")
    }
}
