package com.sedmelluq.discord.lavaplayer.container.playlists.cmaf;

public class Mp4Box {
  public final int start;
  public final int headerSize;
  public final long size;
  public final String type;

  public Mp4Box(int start, int headerSize, long size, String type) {
    this.start = start;
    this.headerSize = headerSize;
    this.size = size;
    this.type = type;
  }

  public int payloadStart() {
    return start + headerSize;
  }

  public int end() {
    long end = start + size;
    return end > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) end;
  }
}
