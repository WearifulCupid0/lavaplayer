package com.sedmelluq.discord.lavaplayer.container.latm;

import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.io.InputStream;

public class LatmAudioTrack extends DelegatedAudioTrack {
  private final InputStream inputStream;

  public LatmAudioTrack(AudioTrackInfo trackInfo, InputStream inputStream) {
    super(trackInfo);
    this.inputStream = inputStream;
  }

  @Override
  public void process(LocalAudioTrackExecutor executor) throws Exception {
    processDelegate(new AdtsAudioTrack(trackInfo, new LatmToAdtsInputStream(inputStream)), executor);
  }
}
