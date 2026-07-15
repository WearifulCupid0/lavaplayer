package com.sedmelluq.discord.lavaplayer.container.playlists.cmaf;

public class HlsCmafSegment {
  public final long sequence;
  public final String url;
  public final HlsByteRange byteRange;
  public final String initUrl;
  public final HlsByteRange initByteRange;
  public final long durationMs;

  public HlsCmafSegment(long sequence, String url, HlsByteRange byteRange, String initUrl, HlsByteRange initByteRange, long durationMs) {
    this.sequence = sequence;
    this.url = url;
    this.byteRange = byteRange;
    this.initUrl = initUrl;
    this.initByteRange = initByteRange;
    this.durationMs = durationMs;
  }
}
