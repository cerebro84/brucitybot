package forty2apps

interface BotConfig {
    fun username(): String
    fun token(): String
    fun cacheFile(): String
}
