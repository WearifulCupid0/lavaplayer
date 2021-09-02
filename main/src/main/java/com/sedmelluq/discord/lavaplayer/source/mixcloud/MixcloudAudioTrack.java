package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audio track that handles processing Mixcloud tracks.
 */
public class MixcloudAudioTrack extends DelegatedAudioTrack {
  private static final Logger log = LoggerFactory.getLogger(MixcloudAudioTrack.class);

  private final MixcloudAudioSourceManager sourceManager;

  /**
   * @param trackInfo Track info
   * @param sourceManager Source manager which was used to find this track
   */
  public MixcloudAudioTrack(AudioTrackInfo trackInfo, MixcloudAudioSourceManager sourceManager) {
    super(trackInfo);

    this.sourceManager = sourceManager;
  }

  @Override
  public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
    try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
      playFromIdentifier(httpInterface, trackInfo.identifier, localExecutor);
    }
  }

  private void playFromIdentifier(
      HttpInterface httpInterface,
      String identifier,
      LocalAudioTrackExecutor localExecutor
  ) throws Exception {
    String playbackUrl = sourceManager.formatHandler.getPlaybackUrl(identifier);

    if (playbackUrl != null && playbackUrl.contains(".m4a")) {
      loadFromMpegUrl(localExecutor, httpInterface, playbackUrl);
      return;
    }

    if (playbackUrl != null && playbackUrl.contains(".mp3")) {
      loadFromMp3Url(localExecutor, httpInterface, playbackUrl);
      return;
    }

    //String hlsPlaybackUrl = sourceManager.formatHandler.getHLSPlaybackUrl(identifier);

    //if (hlsPlaybackUrl != null) {
      //processDelegate(new MixcloudM3uAudioTrack(trackInfo, httpInterface, hlsPlaybackUrl), localExecutor);
      //return;
    //}
  }

  private void loadFromMpegUrl(
      LocalAudioTrackExecutor localExecutor,
      HttpInterface httpInterface,
      String trackUrl
  ) throws Exception {
    log.debug("Starting Mixcloud track from URL: {}", trackUrl);

    try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackUrl), null)) {
      if (!HttpClientTools.isSuccessWithContent(stream.checkStatusCode())) {
        throw new IOException("Invalid status code for Mixcloud stream: " + stream.checkStatusCode());
      }

      processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
    }
  }

  private void loadFromMp3Url(
      LocalAudioTrackExecutor localExecutor,
      HttpInterface httpInterface,
      String trackUrl
  ) throws Exception {
    log.debug("Starting Mixcloud track from URL: {}", trackUrl);

    try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackUrl), null)) {
      if (!HttpClientTools.isSuccessWithContent(stream.checkStatusCode())) {
        throw new IOException("Invalid status code for Mixcloud stream: " + stream.checkStatusCode());
      }

      processDelegate(new Mp3AudioTrack(trackInfo, stream), localExecutor);
    }
  }

  @Override
  protected AudioTrack makeShallowClone() {
    return new MixcloudAudioTrack(trackInfo, sourceManager);
  }

  @Override
  public AudioSourceManager getSourceManager() {
    return sourceManager;
  }
}
