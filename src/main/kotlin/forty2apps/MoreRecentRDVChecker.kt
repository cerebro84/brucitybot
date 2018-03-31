package forty2apps

import org.apache.commons.io.IOUtils
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.logging.Logger
import java.util.regex.Pattern

internal class MoreRecentRDVChecker {
    private val p = Pattern
            .compile("[\\s\\S]*format: 'yyyy-mm-dd'[\\s\\S]*date: '(.*)'[\\s\\S]*")
    private val previousPattern = Pattern
            .compile("[\\s\\S]*.*<dt>Date précédente:</dt>[\\s]*<dd>\\w*\\s(.*)</dd>\\s[\\s\\S]*")

    @Throws(IOException::class)
    fun maybeGetNewDate(url: String): RendezVous {
        val httpclient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD)
                        .setCircularRedirectsAllowed(true)
                        .setMaxRedirects(10)
                        .build())
                .build()
        val httpGet = HttpGet(url)
        LOGGER.info("Retrieving page")
        val html = IOUtils
                .toString(httpclient.execute(httpGet).entity.content, "UTF-8")
        val m = p.matcher(html)
        m.matches()
        val nextDate = m.group(1)

        val previousMatcher = previousPattern.matcher(html)
        previousMatcher.matches()
        val previousDate = previousMatcher.group(1)
        val prev = LocalDate.parse(previousDate, DateTimeFormatter.ofPattern("dd MMMM yyyy").withLocale(java.util.Locale.FRENCH))
        val next = LocalDate.parse(nextDate, DateTimeFormatter.ofPattern("yyyy-MM-dd").withLocale(java.util.Locale.FRENCH))

        LOGGER.info(String.format("Old date: %s, new date: %s", previousDate, nextDate))
        return RendezVous(prev, next)
    }

    companion object {

        private val LOGGER = Logger.getLogger("MoreRecentRDVChecker")
    }

}
