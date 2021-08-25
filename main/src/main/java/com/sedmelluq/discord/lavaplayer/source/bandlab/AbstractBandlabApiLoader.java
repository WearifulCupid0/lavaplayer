package com.sedmelluq.discord.lavaplayer.source.bandlab;

import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.FAULT;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public abstract class AbstractBandlabApiLoader implements BandlabApiLoader {
  protected HttpInterfaceManager httpInterfaceManager;

  AbstractBandlabApiLoader() {
    httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
  }

  protected <T> T extractFromApi(String url, ApiExtractor<T> extractor) {
    try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
      String responseText;
      try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(url))) {
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
      return extractor.extract(httpInterface, response);
    } catch (Exception e) {
      throw ExceptionTools.wrapUnfriendlyExceptions("Loading information for a Bandlab track failed.", FAULT, e);
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
