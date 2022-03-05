package com.sedmelluq.discord.lavaplayer.source.instagram;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
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
 * Audio track that handles processing Instagram tracks.
 */
public class InstagramAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(InstagramAudioTrack.class);

  private final InstagramAudioSourceManager sourceManager;

  /**
   * @param trackInfo Track info
   * @param sourceManager Source manager which was used to find this track
   */
  public InstagramAudioTrack(AudioTrackInfo trackInfo, InstagramAudioSourceManager sourceManager) {
    super(trackInfo);

    this.sourceManager = sourceManager;
  }

  @Override
  public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
    try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
      String playbackUrl = loadPlaybackUrl(httpInterface);

      log.debug("Starting Instagram track from URL: {}", playbackUrl);

      try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), null)) {
        processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
      }
    }
  }

  private String loadPlaybackUrl(HttpInterface httpInterface) throws IOException {
    JsonBrowser data = sourceManager.loadFromApi(String.format("https://www.instagram.com/p/%s/", trackInfo.identifier));
    if (data == null || data.get("graphql").get("shortcode_media").isNull()) {
      throw new FriendlyException("Track information not present on the page.", SUSPICIOUS, null);
    }
    JsonBrowser shortcodeMedia = data.get("graphql").get("shortcode_media");
    if (!shortcodeMedia.get("has_audio").asBoolean(false)) {
        throw new FriendlyException("Track doesn't contains an audio.", SUSPICIOUS, null);
    }

    return shortcodeMedia.get("video_url").safeText();
  }

  @Override
  protected AudioTrack makeShallowClone() {
    return new InstagramAudioTrack(trackInfo, sourceManager);
  }

  @Override
  public InstagramAudioSourceManager getSourceManager() {
    return sourceManager;
  }
}
