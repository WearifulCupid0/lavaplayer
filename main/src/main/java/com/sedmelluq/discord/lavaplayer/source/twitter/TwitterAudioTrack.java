package com.sedmelluq.discord.lavaplayer.source.twitter;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
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
 * Audio track that handles processing Twitter tracks.
 */
public class TwitterAudioTrack extends DelegatedAudioTrack {
  private static final Logger log = LoggerFactory.getLogger(TwitterAudioTrack.class);

  private final TwitterAudioSourceManager sourceManager;

  /**
   * @param trackInfo Track info
   * @param sourceManager Source manager which was used to find this track
   */
  public TwitterAudioTrack(AudioTrackInfo trackInfo, TwitterAudioSourceManager sourceManager) {
    super(trackInfo);

    this.sourceManager = sourceManager;
  }

  @Override
  public void process(LocalAudioTrackExecutor executor) throws Exception {
    try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
      log.debug("Starting Twitter track from URL: {}", trackInfo.identifier);

      try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackInfo.identifier), null)) {
        processDelegate(new MpegAudioTrack(trackInfo, stream), executor);
      }
    }
  }

  @Override
  protected AudioTrack makeShallowClone() {
    return new TwitterAudioTrack(trackInfo, sourceManager);
  }

  @Override
  public TwitterAudioSourceManager getSourceManager() {
    return sourceManager;
  }
}