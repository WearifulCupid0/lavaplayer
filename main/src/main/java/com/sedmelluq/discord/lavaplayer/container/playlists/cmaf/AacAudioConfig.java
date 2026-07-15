package com.sedmelluq.discord.lavaplayer.container.playlists.cmaf;

public class AacAudioConfig {
  public final int audioObjectType;
  public final int adtsProfile;
  public final int sampleRateIndex;
  public final int channelConfig;

  public AacAudioConfig(int audioObjectType, int sampleRateIndex, int channelConfig) {
    this.audioObjectType = audioObjectType;
    this.adtsProfile = Math.max(0, Math.min(3, audioObjectType - 1));
    this.sampleRateIndex = sampleRateIndex;
    this.channelConfig = channelConfig;
  }

  public byte[] createAdtsHeader(int payloadLength) {
    int frameLength = payloadLength + 7;
    byte[] header = new byte[7];

    header[0] = (byte) 0xFF;
    header[1] = (byte) 0xF1;
    header[2] = (byte) (((adtsProfile & 0x03) << 6) | ((sampleRateIndex & 0x0F) << 2) | ((channelConfig >> 2) & 0x01));
    header[3] = (byte) (((channelConfig & 0x03) << 6) | ((frameLength >> 11) & 0x03));
    header[4] = (byte) ((frameLength >> 3) & 0xFF);
    header[5] = (byte) (((frameLength & 0x07) << 5) | 0x1F);
    header[6] = (byte) 0xFC;

    return header;
  }
}
