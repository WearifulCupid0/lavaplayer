package com.sedmelluq.discord.lavaplayer.container.playlists.cmaf;

import java.util.Collections;
import java.util.Map;

public class AacAudioConfig {
  public final int audioObjectType;
  public final int adtsProfile;
  public final int sampleRateIndex;
  public final int channelConfig;

  /**
   * MP4 track id of the audio track. audio-only callers may leave it as -1.
   */
  public final int trackId;

  /**
   * trex defaults indexed by track id. Needed when tfhd/trun omit defaults.
   */
  public final Map<Integer, TrackDefaults> trackDefaults;

  public AacAudioConfig(int audioObjectType, int sampleRateIndex, int channelConfig) {
    this(audioObjectType, sampleRateIndex, channelConfig, -1, Collections.emptyMap());
  }

  public AacAudioConfig(
      int audioObjectType,
      int sampleRateIndex,
      int channelConfig,
      int trackId,
      Map<Integer, TrackDefaults> trackDefaults
  ) {
    this.audioObjectType = audioObjectType;
    this.adtsProfile = Math.max(0, Math.min(3, audioObjectType - 1));
    this.sampleRateIndex = sampleRateIndex;
    this.channelConfig = channelConfig;
    this.trackId = trackId;
    this.trackDefaults = trackDefaults == null ? Collections.emptyMap() : trackDefaults;
  }

  public TrackDefaults defaultsForTrack(int id) {
    TrackDefaults defaults = trackDefaults.get(id);
    return defaults == null ? TrackDefaults.EMPTY : defaults;
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

  public static class TrackDefaults {
    public static final TrackDefaults EMPTY = new TrackDefaults(0L, 0L, 0L);

    public final long defaultSampleDuration;
    public final long defaultSampleSize;
    public final long defaultSampleFlags;

    public TrackDefaults(long defaultSampleDuration, long defaultSampleSize, long defaultSampleFlags) {
      this.defaultSampleDuration = defaultSampleDuration;
      this.defaultSampleSize = defaultSampleSize;
      this.defaultSampleFlags = defaultSampleFlags;
    }
  }
}
