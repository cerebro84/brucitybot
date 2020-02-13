package forty2apps

import org.apache.commons.validator.routines.UrlValidator
import org.apache.logging.log4j.LogManager
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Chat
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
import kotlin.collections.HashMap

internal class BruCityBot(private val botConfig: BotConfig) : TelegramLongPollingBot() {
    private val urlValidator = UrlValidator()
    private val moreRecentRDVChecker = MoreRecentRDVChecker()
    private val cache: MutableMap<Long, MutableMap<String, Any>> = mutableMapOf()

    init {
        readCache()
        val exec = Executors.newSingleThreadScheduledExecutor()
        exec.scheduleWithFixedDelay({
            cache.forEach { chatId, _ ->
                LOGGER.info("Checking for new date")
                checkForNewDate(chatId)
            }
        }, 0, 600, TimeUnit.SECONDS)
    }

    private fun checkForNewDate(key: Long?) {
        val executor = Executors.newSingleThreadExecutor()
        val stuffToDo = fun() {
            val cacheForChat = cache[key] ?: return

            val chat = cacheForChat["chat"] as Chat?
            LOGGER.info("[{}][{} {}] Checking for better rdv", chat?.userName, chat?.firstName, chat?.lastName)
            cacheForChat[CACHE_KEY_LAST_CHECK] = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
            val rendezVousInfoResult = moreRecentRDVChecker.maybeGetNewDate(cacheForChat["url"]?.toString()!!)
            rendezVousInfoResult.fold({ rendezVousInfo: RendezVous ->
                cacheForChat["lastExit"] = if (rendezVousInfo.newDateIsBetter()) {
                    "New date found:" + rendezVousInfo.newPossibleDate
                } else {
                    "New date was in the future: " + rendezVousInfo.newPossibleDate
                }
                cacheForChat["newDate"] = rendezVousInfo.newPossibleDate.toString()
                cacheForChat["rdvDate"] = rendezVousInfo.rdvDate.toString()
                if (rendezVousInfo.newDateIsBetter()) {
                    sendTextMessage(key!!, String
                            .format("A better date is available: %s; previous date: %s", rendezVousInfo.newPossibleDate,
                                    rendezVousInfo.rdvDate))
                }
            }, { e ->
                when(e) {
                    is RdvExpired -> {
                        cacheForChat["lastExit"] = "Rdv expired "
                        sendTextMessage(key!!, "Your RDV already took place, stopping bot")
                        LOGGER.info("[{}][{} {}] Removing from cache because of expired RDV", chat?.userName, chat?.firstName, chat?.lastName)
                        cache.remove(key)
                        saveCache()
                    }
                    else -> {
                        cacheForChat["lastExit"] = "Failure: " + e.message
                        LOGGER.error("Error occurred", e)
                    }
                }

            })
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
                    cache[chatId]?.set("chat", update.message.chat)
                    saveCache()
                    checkForNewDate(chatId)
                }
                message.toLowerCase().startsWith("/start") -> sendTextMessage(chatId!!, "BOT started, send me a web address")
                else -> {
                    val currentCache: MutableMap<String, Any> = cache.getOrDefault(chatId, HashMap<String, Any>() as MutableMap<String, Any>)
                    when {
                        message.toLowerCase().startsWith("/lastcheck") ->
                            sendTextMessage(chatId!!, "Last check: " + currentCache.getOrDefault(CACHE_KEY_LAST_CHECK, ""))
                        message.toLowerCase().startsWith("/lastexit") -> sendTextMessage(chatId!!, "Last exit: " + currentCache
                                .getOrDefault("lastExit", ""))
                        message.toLowerCase().startsWith("/rdvinfo") -> sendTextMessage(chatId!!, String.format("Last check: %s;%nnext date: %s;%ncurrent rdv: %s",
                                currentCache.getOrDefault(CACHE_KEY_LAST_CHECK, ""),
                                currentCache.getOrDefault("newDate", ""),
                                currentCache.getOrDefault("rdvDate", "")))
                        message.toLowerCase().startsWith("/stop") -> {
                            sendTextMessage(chatId!!, "OK, I won't check your RDV anymore. If you want to resume checking, please send me the link again")
                            LOGGER.info("[{}][{} {}] Removing from cache because of user request", update.message.chat?.userName, update.message.chat?.firstName, update.message.chat?.lastName)
                            cache.remove(chatId)
                            saveCache()
                        }
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
            ObjectInputStream(FileInputStream(botConfig.cacheFile())).use { stream -> cache.putAll(stream.readObject() as Map<Long, MutableMap<String, Any>>) }
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
