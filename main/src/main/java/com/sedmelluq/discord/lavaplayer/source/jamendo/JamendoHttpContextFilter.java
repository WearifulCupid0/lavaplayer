package com.sedmelluq.discord.lavaplayer.source.jamendo;

import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.client.utils.URIBuilder;
import java.net.URI;
import java.net.URISyntaxException;

public class JamendoHttpContextFilter implements HttpContextFilter {

  private final JamendoAudioSourceManager sourceManager;

  public JamendoHttpContextFilter(JamendoAudioSourceManager sourceManager) {
    this.sourceManager = sourceManager;
  }

  @Override
  public void onContextOpen(HttpClientContext context) {
    CookieStore cookieStore = context.getCookieStore();

    if (cookieStore == null) {
      cookieStore = new BasicCookieStore();
      context.setCookieStore(cookieStore);
    }

    // Reset cookies for each sequence of requests.
    cookieStore.clear();
  }

  @Override
  public void onContextClose(HttpClientContext context) {
    
  }

  @Override
  public void onRequest(HttpClientContext context, HttpUriRequest request, boolean isRepetition) {
    request.setHeader("User-Agent", "Jamendo-API");

    if (request.getURI().getHost().contains("mp3d.jamendo.com")) {
      //For audio CDN if you send client_id and format query parameters will broke url
      return;
    }

    try {
      URI uri = new URIBuilder(request.getURI())
          .setParameter("client_id", sourceManager.getClientId())
          .setParameter("format", "json")
          .build();

      if (request instanceof HttpRequestBase) {
        ((HttpRequestBase) request).setURI(uri);
      } else {
        throw new IllegalStateException("Cannot update request URI.");
      }
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean onRequestResponse(HttpClientContext context, HttpUriRequest request, HttpResponse response) {
    return false;
  }

  @Override
  public boolean onRequestException(HttpClientContext context, HttpUriRequest request, Throwable error) {
    return false;
  }
}