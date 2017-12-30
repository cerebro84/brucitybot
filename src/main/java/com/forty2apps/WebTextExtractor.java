package com.forty2apps;

import de.l3s.boilerpipe.extractors.ArticleExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.BoilerpipeContentHandler;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

class WebTextExtractor {

  public String parseToPlainText(String url) throws IOException, SAXException, TikaException {
    final BoilerpipeContentHandler handler = new BoilerpipeContentHandler(new BodyContentHandler(),
        ArticleExtractor.INSTANCE);
    final HtmlParser parser = new HtmlParser();
    final ParseContext parseContext = new ParseContext();
    Metadata metadata = new Metadata();
    boolean redirected = true;
    while (redirected) {
      URL resourceUrl = new URL(url);
      HttpURLConnection conn = (HttpURLConnection) resourceUrl.openConnection();

      conn.setConnectTimeout(15000);
      conn.setReadTimeout(15000);
      conn.setInstanceFollowRedirects(false);
      conn.setRequestProperty("User-Agent", "Mozilla/5.0...");

      int i = conn.getResponseCode();
      if (i == HttpURLConnection.HTTP_MOVED_PERM || i == HttpURLConnection.HTTP_MOVED_TEMP) {
        String location = conn.getHeaderField("Location");
        location = URLDecoder.decode(location, "UTF-8");
        URL base = new URL(url);
        URL next = new URL(base, location);  // Deal with relative URLs
        url = next.toExternalForm();
      } else {
        redirected = false;
      }
    }

    try (InputStream stream = new URL(url).openStream()) {
      parser.parse(stream, handler, metadata, parseContext);
      String text = handler.getTextDocument().getText(true, false);
      if (StringUtils.isEmpty(text)) {
        return handler.getTextDocument().getText(true, true);
      }
      return text;
    }
  }

}