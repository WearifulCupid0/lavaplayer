package com.sedmelluq.discord.lavaplayer.source.tiktok;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio track that handles processing TikTok tracks.
 */
public class TiktokAudioTrack extends DelegatedAudioTrack {
  private static final Logger log = LoggerFactory.getLogger(TiktokAudioTrack.class);

  private final TiktokAudioSourceManager sourceManager;

  /**
   * @param trackInfo Track info
   * @param sourceManager Source manager which was used to find this track
   */
  public TiktokAudioTrack(AudioTrackInfo trackInfo, TiktokAudioSourceManager sourceManager) {
    super(trackInfo);

    this.sourceManager = sourceManager;
  }

  @Override
  public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
    try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
      String playbackUrl = loadPlaybackUrl(httpInterface);

      log.debug("Starting TikTok track from URL: {}", playbackUrl);

      try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), null)) {
        processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
      }
    }
  }

  private String loadPlaybackUrl(HttpInterface httpInterface) throws IOException {
    JsonBrowser data = sourceManager.loadFromApi(trackInfo.identifier);
    if (data == null || data.get("aweme_detail").get("video").isNull()) {
      throw new FriendlyException("Failed to get TikTok video data", SUSPICIOUS, null);
    }

    String url = data.get("aweme_detail").get("video").get("play_addr").get("url_list").index(0).text();
    if (url == null || url.isEmpty()) {
      throw new FriendlyException("Failed to get TikTok playback url", SUSPICIOUS, null);
    }

    return url;
  }

  @Override
  protected AudioTrack makeShallowClone() {
    return new TiktokAudioTrack(trackInfo, sourceManager);
  }

  @Override
  public AudioSourceManager getSourceManager() {
    return sourceManager;
  }
}
