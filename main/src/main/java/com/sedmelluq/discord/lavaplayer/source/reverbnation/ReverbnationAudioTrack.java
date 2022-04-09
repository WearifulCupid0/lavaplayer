package com.sedmelluq.discord.lavaplayer.source.reverbnation;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Audio track that handles processing Reverbnation tracks.
 */
public class ReverbnationAudioTrack extends DelegatedAudioTrack {
  private static final Logger log = LoggerFactory.getLogger(ReverbnationAudioTrack.class);

  private final ReverbnationAudioSourceManager sourceManager;

  /**
   * @param trackInfo Track info
   * @param sourceManager Source manager which was used to find this track
   */
  public ReverbnationAudioTrack(AudioTrackInfo trackInfo, ReverbnationAudioSourceManager sourceManager) {
    super(trackInfo);

    this.sourceManager = sourceManager;
  }

  @Override
  public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
    try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
      log.debug("Loading Reverbnation track page from ID: {}", trackInfo.identifier);

      String trackMediaUrl = getTrackMediaUrl(httpInterface);
      log.debug("Starting Reverbnation track from URL: {}", trackMediaUrl);

      try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackMediaUrl), null)) {
        processDelegate(new Mp3AudioTrack(trackInfo, stream), localExecutor);
      }
    }
  }

  private String getTrackMediaUrl(HttpInterface httpInterface) throws IOException {
    try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://api.reverbnation.com/song/" + trackInfo.identifier))) {
      int statusCode = response.getStatusLine().getStatusCode();
      if (!HttpClientTools.isSuccessWithContent(statusCode)) {
        throw new IOException("Invalid status code for track api info: " + statusCode);
      }

      String responseData = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
      JsonBrowser json = JsonBrowser.parse(responseData);
      if (json.get("url").isNull() || json.get("url").safeText().isEmpty()) {
        throw new IOException("Reverbnation playback URL not found on track api info.");
      }

      return json.get("url").text();
    }
  }

  @Override
  protected AudioTrack makeShallowClone() {
    return new ReverbnationAudioTrack(trackInfo, sourceManager);
  }

  @Override
  public ReverbnationAudioSourceManager getSourceManager() {
    return sourceManager;
  }
}
