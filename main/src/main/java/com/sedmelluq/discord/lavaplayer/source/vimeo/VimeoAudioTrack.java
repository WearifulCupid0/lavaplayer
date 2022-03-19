package com.sedmelluq.discord.lavaplayer.source.vimeo;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio track that handles processing Vimeo tracks.
 */
public class VimeoAudioTrack extends DelegatedAudioTrack {
  private static final Logger log = LoggerFactory.getLogger(VimeoAudioTrack.class);

  private final VimeoAudioSourceManager sourceManager;

  /**
   * @param trackInfo Track info
   * @param sourceManager Source manager which was used to find this track
   */
  public VimeoAudioTrack(AudioTrackInfo trackInfo, VimeoAudioSourceManager sourceManager) {
    super(trackInfo);

    this.sourceManager = sourceManager;
  }

  @Override
  public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
    try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
      String playbackUrl = loadPlaybackUrl(httpInterface);

      log.debug("Starting Vimeo track from URL: {}", playbackUrl);

      try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), null)) {
        processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
      }
    }
  }

  private String loadPlaybackUrl(HttpInterface httpInterface) throws Exception {
    JsonBrowser config = this.sourceManager.requestPage(URI.create(this.trackInfo.identifier), "window.vimeo.clip_page_config = ", "\n");
    if (config.isNull() || config.get("player").isNull()) {
      throw new FriendlyException("Track information not present on the page.", SUSPICIOUS, null);
    }

    String trackConfigUrl = config.get("player").get("config_url").text();
    if (trackConfigUrl == null || trackConfigUrl.isEmpty()) {
      throw new FriendlyException("Player config url not present on the page", COMMON, null);
    }

    JsonBrowser trackConfig = loadTrackConfig(httpInterface, trackConfigUrl);

    return trackConfig.get("request").get("files").get("progressive").index(0).get("url").text();
  }

  private JsonBrowser loadTrackConfig(HttpInterface httpInterface, String trackAccessInfoUrl) throws IOException {
    try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(trackAccessInfoUrl))) {
      int statusCode = response.getStatusLine().getStatusCode();

      if (!HttpClientTools.isSuccessWithContent(statusCode)) {
        throw new FriendlyException("Server responded with an error.", SUSPICIOUS,
            new IllegalStateException("Response code for track access info is " + statusCode));
      }

      return JsonBrowser.parse(response.getEntity().getContent());
    }
  }

  @Override
  protected AudioTrack makeShallowClone() {
    return new VimeoAudioTrack(trackInfo, sourceManager);
  }

  @Override
  public VimeoAudioSourceManager getSourceManager() {
    return sourceManager;
  }
}