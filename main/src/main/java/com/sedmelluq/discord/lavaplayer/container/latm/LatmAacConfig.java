package com.sedmelluq.discord.lavaplayer.container.latm;

public class LatmAacConfig {
  public final int audioObjectType;
  public final int samplingFrequencyIndex;
  public final int sampleRate;
  public final int channelConfiguration;

  public LatmAacConfig(int audioObjectType, int samplingFrequencyIndex, int sampleRate, int channelConfiguration) {
    this.audioObjectType = audioObjectType;
    this.samplingFrequencyIndex = samplingFrequencyIndex;
    this.sampleRate = sampleRate;
    this.channelConfiguration = channelConfiguration;
  }

  public int adtsProfile() {
    int headerObjectType = audioObjectType;

    if (headerObjectType == 5 || headerObjectType == 29) {
      headerObjectType = 2;
    }

    return Math.max(0, Math.min(3, headerObjectType - 1));
  }
}
