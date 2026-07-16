package com.sedmelluq.discord.lavaplayer.container.mpegts;

import com.sedmelluq.discord.lavaplayer.container.latm.LatmAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.io.InputStream;

import static com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream.LATM_ELEMENTARY_STREAM;

public class MpegLatmAudioTrack extends DelegatedAudioTrack {
  private final InputStream inputStream;

  public MpegLatmAudioTrack(AudioTrackInfo trackInfo, InputStream inputStream) {
    super(trackInfo);
    this.inputStream = inputStream;
  }

  @Override
  public void process(LocalAudioTrackExecutor executor) throws Exception {
    MpegTsElementaryInputStream elementaryInputStream = new MpegTsElementaryInputStream(inputStream, LATM_ELEMENTARY_STREAM);
    PesPacketInputStream pesPacketInputStream = new PesPacketInputStream(elementaryInputStream);
    processDelegate(new LatmAudioTrack(trackInfo, pesPacketInputStream), executor);
  }
}
