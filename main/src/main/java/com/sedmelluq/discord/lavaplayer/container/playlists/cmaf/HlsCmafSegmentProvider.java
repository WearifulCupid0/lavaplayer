package com.sedmelluq.discord.lavaplayer.container.playlists.cmaf;

import com.sedmelluq.discord.lavaplayer.container.playlists.ExtendedM3uParser;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;
import static com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.fetchResponseLines;

public class HlsCmafSegmentProvider {
  private static final long SEGMENT_WAIT_STEP_MS = 200L;

  private final String streamUrl;
  private final boolean isInnerUrl;

  private String mediaPlaylistUrl;
  private long lastSequence = Long.MIN_VALUE;

  public HlsCmafSegmentProvider(String streamUrl, boolean isInnerUrl) {
    this.streamUrl = streamUrl;
    this.isInnerUrl = isInnerUrl;
    this.mediaPlaylistUrl = isInnerUrl ? streamUrl : null;
  }

  public HlsCmafSegment getNextSegment(HttpInterface httpInterface) throws InterruptedException {
    try {
      String playlistUrl = resolveMediaPlaylistUrl(httpInterface);
      long startTime = System.currentTimeMillis();

      while (true) {
        HlsCmafPlaylist playlist = loadMediaPlaylist(httpInterface, playlistUrl);
        HlsCmafSegment nextSegment = chooseNextSegment(playlist.segments);

        if (nextSegment != null) {
          lastSequence = nextSegment.sequence;
          return nextSegment;
        }

        if (playlist.endList) {
          return null;
        }

        long waitLimit = Math.max(playlist.targetDurationMs, 1000L);

        if (System.currentTimeMillis() - startTime >= waitLimit) {
          return null;
        }

        Thread.sleep(SEGMENT_WAIT_STEP_MS);
      }
    } catch (IOException e) {
      throw new FriendlyException("Failed to get next CMAF HLS segment.", SUSPICIOUS, e);
    }
  }

  private HlsCmafSegment chooseNextSegment(List<HlsCmafSegment> segments) {
    for (HlsCmafSegment segment : segments) {
      if (lastSequence == Long.MIN_VALUE || segment.sequence > lastSequence) {
        return segment;
      }
    }

    return null;
  }

  private String resolveMediaPlaylistUrl(HttpInterface httpInterface) throws IOException {
    if (mediaPlaylistUrl != null) {
      return mediaPlaylistUrl;
    }

    String[] lines = fetchResponseLines(httpInterface, new HttpGet(streamUrl), "HLS stream list");

    if (isMediaPlaylist(lines)) {
      mediaPlaylistUrl = streamUrl;
      return mediaPlaylistUrl;
    }

    String audioUri = findAudioMediaPlaylistUri(lines);

    if (audioUri == null) {
      audioUri = findBestStreamPlaylistUri(lines);
    }

    if (audioUri == null) {
      throw new FriendlyException("No playable media playlist was found in HLS master playlist.", COMMON, null);
    }

    mediaPlaylistUrl = resolveUrl(streamUrl, audioUri);
    return mediaPlaylistUrl;
  }

  public HlsCmafPlaylist loadMediaPlaylist(HttpInterface httpInterface, String playlistUrl) throws IOException {
    String[] lines = fetchResponseLines(httpInterface, new HttpGet(playlistUrl), "CMAF HLS media playlist");

    List<HlsCmafSegment> segments = new ArrayList<>();
    String currentInitUrl = null;
    HlsByteRange currentInitByteRange = null;
    HlsByteRange nextByteRange = null;
    String lastByteRangeUrl = null;
    long lastByteRangeEnd = 0L;
    long mediaSequence = 0L;
    long nextDurationMs = 0L;
    long targetDurationMs = 4000L;
    boolean endList = false;

    for (String lineText : lines) {
      ExtendedM3uParser.Line line = ExtendedM3uParser.parseLine(lineText);

      if (line.isDirective()) {
        if ("EXT-X-KEY".equals(line.directiveName)) {
          validateEncryption(line);
        } else if ("EXT-X-MAP".equals(line.directiveName)) {
          String uri = argument(line, "URI");

          if (isBlank(uri)) {
            throw new FriendlyException("CMAF HLS playlist contains EXT-X-MAP without URI.", COMMON, null);
          }

          currentInitUrl = resolveUrl(playlistUrl, uri);
          currentInitByteRange = parseByteRange(argument(line, "BYTERANGE"), null, 0L);
        } else if ("EXT-X-BYTERANGE".equals(line.directiveName)) {
          nextByteRange = parseByteRange(line.extraData, lastByteRangeUrl, lastByteRangeEnd);
        } else if ("EXTINF".equals(line.directiveName)) {
          nextDurationMs = parseExtInfDuration(line.extraData);
        } else if ("EXT-X-MEDIA-SEQUENCE".equals(line.directiveName)) {
          mediaSequence = parseLong(line.extraData, 0L);
        } else if ("EXT-X-TARGETDURATION".equals(line.directiveName)) {
          targetDurationMs = parseLong(line.extraData, 4L) * 1000L;
        } else if ("EXT-X-ENDLIST".equals(line.directiveName)) {
          endList = true;
        }
      } else if (line.isData()) {
        if (currentInitUrl == null) {
          throw new FriendlyException("CMAF HLS segment found before EXT-X-MAP initialization segment.", COMMON, null);
        }

        String segmentUrl = resolveUrl(playlistUrl, line.lineData);
        HlsByteRange segmentRange = nextByteRange;
        long nextSequence = mediaSequence + segments.size();

        segments.add(new HlsCmafSegment(nextSequence, segmentUrl, segmentRange, currentInitUrl, currentInitByteRange, nextDurationMs));

        if (segmentRange != null) {
          lastByteRangeUrl = segmentUrl;
          lastByteRangeEnd = segmentRange.endExclusive();
        } else {
          lastByteRangeUrl = null;
          lastByteRangeEnd = 0L;
        }

        nextByteRange = null;
        nextDurationMs = 0L;
      }
    }

    return new HlsCmafPlaylist(segments, endList, targetDurationMs);
  }

  private static void validateEncryption(ExtendedM3uParser.Line line) {
    String method = argument(line, "METHOD");

    if (method == null || "NONE".equalsIgnoreCase(method)) {
      return;
    }

    String keyFormat = argument(line, "KEYFORMAT");

    if ("com.apple.streamingkeydelivery".equalsIgnoreCase(keyFormat)) {
      throw new FriendlyException("This HLS stream uses Apple FairPlay DRM, which is not supported.", COMMON, null);
    }

    throw new FriendlyException("Encrypted CMAF HLS streams are not supported by this lavaplayer build: " + method, COMMON, null);
  }

  private static boolean isMediaPlaylist(String[] lines) {
    for (String lineText : lines) {
      ExtendedM3uParser.Line line = ExtendedM3uParser.parseLine(lineText);

      if (line.isDirective() && "EXT-X-STREAM-INF".equals(line.directiveName)) {
        return false;
      }

      if (line.isDirective() && "EXT-X-MEDIA".equals(line.directiveName) && "AUDIO".equalsIgnoreCase(argument(line, "TYPE")) && !isBlank(argument(line, "URI"))) {
        return false;
      }
    }

    return true;
  }

  private static String findAudioMediaPlaylistUri(String[] lines) {
    String fallback = null;

    for (String lineText : lines) {
      ExtendedM3uParser.Line line = ExtendedM3uParser.parseLine(lineText);

      if (!line.isDirective() || !"EXT-X-MEDIA".equals(line.directiveName)) {
        continue;
      }

      if (!"AUDIO".equalsIgnoreCase(argument(line, "TYPE"))) {
        continue;
      }

      String uri = argument(line, "URI");

      if (isBlank(uri)) {
        continue;
      }

      if (fallback == null) {
        fallback = uri;
      }

      if ("YES".equalsIgnoreCase(argument(line, "DEFAULT"))) {
        return uri;
      }
    }

    return fallback;
  }

  /**
   * Picks the cheapest variant which has AAC audio. For audio extraction from video+audio
   * CMAF variants, this avoids downloading 720p/1080p video just to discard it.
   */
  private static String findBestStreamPlaylistUri(String[] lines) {
    ExtendedM3uParser.Line streamInfoLine = null;
    String bestUri = null;
    long bestBandwidth = Long.MAX_VALUE;
    String fallbackUri = null;
    long fallbackBandwidth = Long.MAX_VALUE;

    for (String lineText : lines) {
      ExtendedM3uParser.Line line = ExtendedM3uParser.parseLine(lineText);

      if (line.isDirective() && "EXT-X-STREAM-INF".equals(line.directiveName)) {
        streamInfoLine = line;
      } else if (line.isData() && streamInfoLine != null) {
        long bandwidth = parseLong(argument(streamInfoLine, "BANDWIDTH"), Long.MAX_VALUE);
        String codecs = argument(streamInfoLine, "CODECS");

        if (bandwidth < fallbackBandwidth) {
          fallbackBandwidth = bandwidth;
          fallbackUri = line.lineData;
        }

        if (codecs == null || codecs.toLowerCase(Locale.ROOT).contains("mp4a")) {
          if (bandwidth < bestBandwidth) {
            bestBandwidth = bandwidth;
            bestUri = line.lineData;
          }
        }

        streamInfoLine = null;
      } else if (line.isDirective()) {
        streamInfoLine = null;
      }
    }

    return bestUri != null ? bestUri : fallbackUri;
  }

  private static HlsByteRange parseByteRange(String value, String previousUrl, long previousEnd) {
    if (isBlank(value)) {
      return null;
    }

    String trimmed = value.trim();

    if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
    }

    String[] parts = trimmed.split("@", 2);
    long length = parseLong(parts[0], -1L);

    if (length < 0) {
      return null;
    }

    long offset;

    if (parts.length == 2) {
      offset = parseLong(parts[1], 0L);
    } else if (previousUrl != null) {
      offset = previousEnd;
    } else {
      offset = 0L;
    }

    return new HlsByteRange(length, offset);
  }

  private static long parseExtInfDuration(String value) {
    if (value == null) {
      return 0L;
    }

    String[] parts = value.split(",", 2);

    try {
      return (long) (Double.parseDouble(parts[0].trim()) * 1000.0);
    } catch (NumberFormatException ignored) {
      return 0L;
    }
  }

  private static long parseLong(String value, long fallback) {
    if (value == null) {
      return fallback;
    }

    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static String argument(ExtendedM3uParser.Line line, String name) {
    Object value = line.directiveArguments.get(name.toUpperCase(Locale.ROOT));
    return value == null ? null : value.toString();
  }

  private static String resolveUrl(String playlistUrl, String uri) {
    return URI.create(playlistUrl).resolve(uri).toString();
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
