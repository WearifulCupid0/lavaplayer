package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import java.util.List;

public class DefaultMixcloudFormatHandler implements MixcloudFormatHandler {
    private static final FormatType[] TYPES = FormatType.values();

  @Override
  public MixcloudTrackFormat chooseBestFormat(List<MixcloudTrackFormat> formats) {
        for (FormatType type : TYPES) {
            for (MixcloudTrackFormat format : formats) {
                if (type.matches(format)) {
                    return format;
                }
            }
        }

        throw new RuntimeException("Did not detect any supported formats");
    }

    @Override
    public String buildFormatIdentifier(MixcloudTrackFormat format) {
        for (FormatType type : TYPES) {
            if (type.matches(format)) {
                return type.prefix + format.getPlaybackUrl();
            }
        }

        return "X:" + format.getPlaybackUrl();
    }

    @Override
    public String getMpegPlaybackUrl(String identifier) {
        if (identifier.startsWith("M:")) {
            return identifier.substring(2);
        }

        return null;
    }
    private enum FormatType {
        TYPE_MPEG("progressive", "M:"),
        TYPE_MANIFEST("segments", "S:"),
        TYPE_HLS("hls", "H:");
    
        public final String protocol;
        public final String prefix;
    
        FormatType(String protocol, String prefix) {
          this.protocol = protocol;
          this.prefix = prefix;
        }
    
        public boolean matches(MixcloudTrackFormat format) {
          return protocol.equals(format.getProtocol());
        }
    }
}