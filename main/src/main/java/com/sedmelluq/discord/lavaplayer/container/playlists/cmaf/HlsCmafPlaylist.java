package com.sedmelluq.discord.lavaplayer.container.playlists.cmaf;

import java.util.List;

public class HlsCmafPlaylist {
  public final List<HlsCmafSegment> segments;
  public final boolean endList;
  public final long targetDurationMs;

  public HlsCmafPlaylist(List<HlsCmafSegment> segments, boolean endList, long targetDurationMs) {
    this.segments = segments;
    this.endList = endList;
    this.targetDurationMs = targetDurationMs;
  }
}
