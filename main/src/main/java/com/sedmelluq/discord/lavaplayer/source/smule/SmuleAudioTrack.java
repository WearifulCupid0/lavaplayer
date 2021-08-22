package com.sedmelluq.discord.lavaplayer.source.smule;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

/**
 * Audio track that handles processing Smule tracks.
 */
public class SmuleAudioTrack extends DelegatedAudioTrack {
  private static final Logger log = LoggerFactory.getLogger(SmuleAudioTrack.class);

  private final SmuleAudioSourceManager sourceManager;

  /**
   * @param trackInfo Track info
   * @param sourceManager Source manager which was used to find this track
   */
  public SmuleAudioTrack(AudioTrackInfo trackInfo, SmuleAudioSourceManager sourceManager) {
    super(trackInfo);

    this.sourceManager = sourceManager;
  }

  @Override
  public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
    try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
      log.debug("Loading Smule track page from URL: {}", trackInfo.identifier);

      String trackMediaUrl = getUri(trackInfo.identifier, httpInterface);
      log.debug("Starting Smule track from URL: {}", trackMediaUrl);

      try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackMediaUrl), null)) {
        processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
      }
    }
  }

  private String getUri(String identifier, HttpInterface httpInterface) throws Exception {
    try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(getRefirUri(identifier)))) {
      HttpClientTools.assertSuccessWithContent(response, "redirect response");
      HttpClientContext context = httpInterface.getContext();

      List<URI> redirects = context.getRedirectLocations();
      if (redirects != null && !redirects.isEmpty()) {
        return redirects.get(0).toString();
      } else {
        throw new FriendlyException("Unable to find redirect from smule link", FriendlyException.Severity.SUSPICIOUS, null);
      }
    }
  }

  private URI getRefirUri(String mediaUrl) throws Exception {
    return new URIBuilder("https://www.smule.com/redir")
    .addParameter("e", "1")
    .addParameter("url", mediaUrl).build();
  }

  @Override
  protected AudioTrack makeShallowClone() {
    return new SmuleAudioTrack(trackInfo, sourceManager);
  }

  @Override
  public AudioSourceManager getSourceManager() {
    return sourceManager;
  }
}
