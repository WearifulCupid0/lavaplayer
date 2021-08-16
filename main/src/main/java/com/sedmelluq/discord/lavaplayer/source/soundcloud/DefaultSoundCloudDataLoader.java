package com.sedmelluq.discord.lavaplayer.source.soundcloud;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.TextRange;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class DefaultSoundCloudDataLoader implements SoundCloudDataLoader {
  private static final Logger log = LoggerFactory.getLogger(DefaultSoundCloudDataLoader.class);

  private static final TextRange[] JSON_RANGES = {
    new TextRange("window.__sc_hydration = ", ";</script>"),
    new TextRange("catch(e){}})},", ");</script>"),
    new TextRange("){}})},", ");</script>")
  };

  @Override
  public JsonBrowser load(HttpInterface httpInterface, String url) throws IOException {
    URI uri = URI.create("https://api-v2.soundcloud.com/resolve?url=" + url);
    try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
      JsonBrowser json = null;
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        json = loadFromHTML(httpInterface, url);
        return json;
      }

      HttpClientTools.assertSuccessWithContent(response, "video api response");

      json = JsonBrowser.parse(response.getEntity().getContent());

      if(json.isNull() || json.get("id").isNull()) {
        json = loadFromHTML(httpInterface, url);
      }

      return json;
    }
  }
  private JsonBrowser loadFromHTML(HttpInterface httpInterface, String url) throws IOException {
    try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(url))) {
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return JsonBrowser.NULL_BROWSER;
      }

      HttpClientTools.assertSuccessWithContent(response, "video page response");

      String html = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      String rootData = DataFormatTools.extractBetween(html, JSON_RANGES);

      if (rootData == null) {
        throw new FriendlyException("This url does not appear to be a playable track.", SUSPICIOUS,
            ExceptionTools.throwWithDebugInfo(log, null, "No track JSON found", "html", html));
      }

      return JsonBrowser.parse(rootData);
    }
  }
}
