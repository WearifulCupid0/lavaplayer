package com.sedmelluq.discord.lavaplayer.container.latm;

public final class AdtsHeaderWriter {
  private AdtsHeaderWriter() {
  }

  public static void write(byte[] header, LatmAacConfig config, int payloadLength) {
    int frameLength = payloadLength + 7;
    int profile = config.adtsProfile();
    int frequencyIndex = config.samplingFrequencyIndex;
    int channelConfig = config.channelConfiguration;

    header[0] = (byte) 0xFF;
    header[1] = (byte) 0xF1;
    header[2] = (byte) (((profile & 0x03) << 6) | ((frequencyIndex & 0x0F) << 2) | ((channelConfig >> 2) & 0x01));
    header[3] = (byte) (((channelConfig & 0x03) << 6) | ((frameLength >> 11) & 0x03));
    header[4] = (byte) ((frameLength >> 3) & 0xFF);
    header[5] = (byte) (((frameLength & 0x07) << 5) | 0x1F);
    header[6] = (byte) 0xFC;
  }
}
