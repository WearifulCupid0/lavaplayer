package com.sedmelluq.discord.lavaplayer.container.playlists.cmaf;

public class HlsByteRange {
  public final long length;
  public final long offset;

  public HlsByteRange(long length, long offset) {
    if (length < 0) {
      throw new IllegalArgumentException("Byte range length cannot be negative.");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Byte range offset cannot be negative.");
    }

    this.length = length;
    this.offset = offset;
  }

  public String toHttpRangeHeader() {
    return "bytes=" + offset + "-" + (offset + length - 1);
  }

  public long endExclusive() {
    return offset + length;
  }
}
