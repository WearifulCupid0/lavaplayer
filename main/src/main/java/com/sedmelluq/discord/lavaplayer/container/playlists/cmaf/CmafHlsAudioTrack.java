package com.sedmelluq.discord.lavaplayer.container.playlists.cmaf;

import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CmafHlsAudioTrack extends DelegatedAudioTrack {
  private static final Logger log = LoggerFactory.getLogger(CmafHlsAudioTrack.class);

  private final String streamUrl;
  private final HttpInterfaceManager httpInterfaceManager;
  private final boolean isInnerUrl;

  public CmafHlsAudioTrack(AudioTrackInfo trackInfo, String streamUrl, HttpInterfaceManager httpInterfaceManager, boolean isInnerUrl) {
    super(trackInfo);
    this.streamUrl = streamUrl;
    this.httpInterfaceManager = httpInterfaceManager;
    this.isInnerUrl = isInnerUrl;
  }

  @Override
  public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
    log.debug("Starting to play CMAF HLS stream {}.", getIdentifier());

    try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
      HlsCmafSegmentProvider segmentProvider = new HlsCmafSegmentProvider(streamUrl, isInnerUrl);
      FragmentedMp4AdtsInputStream adtsInputStream = new FragmentedMp4AdtsInputStream(httpInterface, segmentProvider);

      processDelegate(new AdtsAudioTrack(trackInfo, adtsInputStream), localExecutor);
    }
  }
}
