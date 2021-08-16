package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import java.util.List;

public interface MixcloudFormatHandler {
  MixcloudTrackFormat chooseBestFormat(List<MixcloudTrackFormat> formats);

  String buildFormatIdentifier(MixcloudTrackFormat format);

  String getMpegPlaybackUrl(String identifier);

  String getHLSPlaybackUrl(String identifier);
}
