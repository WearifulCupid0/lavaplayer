package com.sedmelluq.discord.lavaplayer.source.vimeo;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
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
      String playbackUrl = loadPlaybackUrl();

      log.debug("Starting Vimeo track from URL: {}", playbackUrl);

      try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), null)) {
        processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
      }
    }
  }

  private String loadPlaybackUrl() throws IOException {
    JsonBrowser config = sourceManager.fetchPlayerConfig(this.trackInfo.identifier);

    String url = config.get("request").get("files").get("progressive").index(0).get("url").text();

    if (url == null || url.isEmpty()) {
      throw new IOException("Playback url not found on player config.");
    }

    return url;
  }

  @Override
  protected AudioTrack makeShallowClone() {
    return new VimeoAudioTrack(trackInfo, sourceManager);
  }

  @Override
  public AudioSourceManager getSourceManager() {
    return sourceManager;
  }
}
