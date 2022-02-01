package com.sedmelluq.discord.lavaplayer.source.odysee;

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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio track that handles processing Odysee tracks.
 */
public class OdyseeAudioTrack extends DelegatedAudioTrack {
  private static final Logger log = LoggerFactory.getLogger(OdyseeAudioTrack.class);

  private final OdyseeAudioSourceManager sourceManager;

  /**
   * @param trackInfo Track info
   * @param sourceManager Source manager which was used to find this track
   */
  public OdyseeAudioTrack(AudioTrackInfo trackInfo, OdyseeAudioSourceManager sourceManager) {
    super(trackInfo);

    this.sourceManager = sourceManager;
  }

  @Override
  public void process(LocalAudioTrackExecutor executor) throws Exception {
    try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
      String playbackUrl = loadPlayBackUrl(httpInterface);

      log.debug("Starting Odysee track from URL: {}", playbackUrl);

      try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), null)) {
        processDelegate(new MpegAudioTrack(trackInfo, stream), executor);
      }
    } catch (IOException e) {
      throw new FriendlyException("Loading track from Odysee failed.", SUSPICIOUS, e);
    }
  }

  private String loadPlayBackUrl(HttpInterface httpInterface) throws IOException {
    HttpPost post = new HttpPost(OdyseeConstants.API_URL);

    post.addHeader("X-Lbry-Auth-Token", OdyseeConstants.ODYSEE_AUTH_KEY);

    post.setEntity(new StringEntity(String.format(OdyseeConstants.GET_PAYLOAD, trackInfo.identifier), ContentType.APPLICATION_JSON));

    try (CloseableHttpResponse response = httpInterface.execute(post)) {
      int statusCode = response.getStatusLine().getStatusCode();

      if (!HttpClientTools.isSuccessWithContent(statusCode)) {
        throw new IOException("Unexpected status code from playback parameters page: " + statusCode);
      }

      JsonBrowser playbackUrl = JsonBrowser.parse(response.getEntity().getContent()).get("result").get("streaming_url");

      if (playbackUrl.isNull()) throw new IOException("Couldn't get playbackUrl from Odysee track with identifier: " + trackInfo.identifier);

      return playbackUrl.text();
    }
  }

  @Override
  protected AudioTrack makeShallowClone() {
    return new OdyseeAudioTrack(trackInfo, sourceManager);
  }

  @Override
  public OdyseeAudioSourceManager getSourceManager() {
    return sourceManager;
  }
}