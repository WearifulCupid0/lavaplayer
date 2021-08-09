package com.sedmelluq.discord.lavaplayer.source.saavn;

import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.FAULT;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public abstract class AbstractSaavnApiLoader implements SaavnApiLoader {

  protected HttpInterfaceManager httpInterfaceManager;

  AbstractSaavnApiLoader() {
    httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
  }

  protected <T> T extractFromApi(String caller, String query, ApiExtractor<T> extractor) {
    try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
      String responseText;
      RequestConfig config = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
      URI uri = URI.create("https://www.jiosaavn.com/api.php?_format=json&_marker=0&__call=" + caller + "&" + query);
      HttpGet get = new HttpGet(uri);
      get.setHeader("Accept", "application/json");
      get.setConfig(config);
      try (CloseableHttpResponse response = httpInterface.execute(get)) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
          throw new IOException("Invalid status code: " + statusCode);
        }
        responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
      }
      JsonBrowser response = JsonBrowser.parse(responseText);
      if (response.isNull()) {
        throw new FriendlyException("Couldn't get API response.", SUSPICIOUS, null);
      }
      if (response.isNull()) {
        throw new FriendlyException("Couldn't get API response result.", SUSPICIOUS, null);
      }
      return extractor.extract(httpInterface, response);
    } catch (Exception e) {
      throw ExceptionTools.wrapUnfriendlyExceptions("Loading information for a JioSaavn track failed.", FAULT, e);
    }
  }

  @Override
  public ExtendedHttpConfigurable getHttpConfiguration() {
    return httpInterfaceManager;
  }

  @Override
  public void shutdown() {
    ExceptionTools.closeWithWarnings(httpInterfaceManager);
  }

  protected interface ApiExtractor<T> {
    T extract(HttpInterface httpInterface, JsonBrowser result) throws Exception;
  }
}
