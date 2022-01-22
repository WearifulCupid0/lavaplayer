package com.sedmelluq.discord.lavaplayer.source.jamendo;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Audio track that handles processing Jamendo tracks.
 */
public class JamendoAudioTrack extends DelegatedAudioTrack {
  private static final Logger log = LoggerFactory.getLogger(JamendoAudioTrack.class);

  private final JamendoAudioSourceManager sourceManager;

  /**
   * @param trackInfo Track info
   * @param sourceManager Source manager which was used to find this track
   */
  public JamendoAudioTrack(AudioTrackInfo trackInfo, JamendoAudioSourceManager sourceManager) {
    super(trackInfo);
    this.sourceManager = sourceManager;
  }

  @Override
  public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
    try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
      String trackMediaUrl = "https://mp3d.jamendo.com/download/track/" + trackInfo.identifier + "/mp32/";
      log.debug("Starting Jamendo track from URL: {}", trackMediaUrl);
      try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackMediaUrl), null)) {
        processDelegate(new Mp3AudioTrack(trackInfo, stream), localExecutor);
      }
    }
  }

  @Override
  public AudioTrack makeClone() {
    return new JamendoAudioTrack(trackInfo, sourceManager);
  }

  @Override
  public AudioSourceManager getSourceManager() {
    return sourceManager;
  }
}