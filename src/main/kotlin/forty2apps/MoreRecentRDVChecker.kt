package forty2apps

import com.github.kittinunf.result.Result
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
    private val expiredRdvCheck = "<h2>Nous sommes d&eacute;sol&eacute;s</h2>"
    private val nextAvailableRdvPattern = Pattern
            .compile("[\\s\\S]*format: 'yyyy-mm-dd'[\\s\\S]*date: '(.*)'[\\s\\S]*")
    private val currentRdvPattern = Pattern
            .compile("[\\s\\S]*.*<dt>Date précédente:</dt>[\\s]*<dd>\\w*\\s(.*)</dd>\\s[\\s\\S]*")

    @Throws(IOException::class)
    fun maybeGetNewDate(url: String): Result<RendezVous, Exception> {
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
        val nextAvailableRdvMatcher = nextAvailableRdvPattern.matcher(html)
        if (nextAvailableRdvMatcher.matches()) {
            val nextDate = nextAvailableRdvMatcher.group(1)

            val currentRdvMatcher = currentRdvPattern.matcher(html)
            currentRdvMatcher.matches()
            val previousDate = currentRdvMatcher.group(1)
            val prev = LocalDate.parse(previousDate, DateTimeFormatter.ofPattern("dd MMMM yyyy").withLocale(java.util.Locale.FRENCH))
            val next = LocalDate.parse(nextDate, DateTimeFormatter.ofPattern("yyyy-MM-dd").withLocale(java.util.Locale.FRENCH))

            LOGGER.info(String.format("Old date: %s, new date: %s", previousDate, nextDate))
            return Result.of(RendezVous(prev, next))
        }
        if (html.contains(expiredRdvCheck, false)) {
            return Result.error(RdvExpired())
        }
        return Result.error(NoResultFound())
    }

    companion object {

        private val LOGGER = Logger.getLogger("MoreRecentRDVChecker")
    }

}
