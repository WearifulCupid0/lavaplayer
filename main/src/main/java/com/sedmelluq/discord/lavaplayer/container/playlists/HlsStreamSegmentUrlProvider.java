package com.sedmelluq.discord.lavaplayer.container.playlists;

import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamSegmentUrlProvider;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.fetchResponseLines;

public class HlsStreamSegmentUrlProvider extends M3uStreamSegmentUrlProvider {
  private static final Logger log = LoggerFactory.getLogger(HlsStreamSegmentUrlProvider.class);

  private static final String EXT_X_STREAM_INF = "EXT-X-STREAM-INF";
  private static final String EXT_X_MEDIA = "EXT-X-MEDIA";
  private static final String TYPE_AUDIO = "AUDIO";

  private final String streamListUrl;
  private volatile String segmentPlaylistUrl;

  public HlsStreamSegmentUrlProvider(String streamListUrl, String segmentPlaylistUrl) {
    super(streamListUrl);
    this.streamListUrl = streamListUrl;
    this.segmentPlaylistUrl = segmentPlaylistUrl;
  }

  @Override
  protected String getQualityFromM3uDirective(ExtendedM3uParser.Line directiveLine) {
    return "default";
  }

  protected boolean isSegmentPlaylist(String[] lines) {
    return Arrays.stream(lines).noneMatch(this::isMasterPlaylistDirective);
  }

  @Override
  protected String fetchSegmentPlaylistUrl(HttpInterface httpInterface) throws IOException {
    if (segmentPlaylistUrl != null) {
      return segmentPlaylistUrl;
    }

    HttpUriRequest request = new HttpGet(streamListUrl);
    String[] lines = fetchResponseLines(httpInterface, request, "HLS stream list");

    if (isSegmentPlaylist(lines)) {
      return (segmentPlaylistUrl = streamListUrl);
    }

    String audioMediaUrl = findAudioMediaPlaylistUrl(lines);

    if (audioMediaUrl != null) {
      segmentPlaylistUrl = createSegmentUrl(streamListUrl, audioMediaUrl);
      log.debug("Chose HLS audio media playlist url {}", segmentPlaylistUrl);
      return segmentPlaylistUrl;
    }

    List<ChannelStreamInfo> streams = loadChannelStreamsList(lines);

    if (streams.isEmpty()) {
      throw new IllegalStateException("No streams listed in HLS stream list.");
    }

    ChannelStreamInfo stream = streams.get(0);
    log.debug("Chose stream with quality {} and url {}", stream.quality, stream.url);

    segmentPlaylistUrl = stream.url;
    return segmentPlaylistUrl;
  }

  @Override
  protected HttpUriRequest createSegmentGetRequest(String url) {
    return new HttpGet(url);
  }

  public static String findHlsEntryUrl(String[] lines) {
    String audioMediaUrl = findAudioMediaPlaylistUrl(lines);

    if (audioMediaUrl != null) {
      return audioMediaUrl;
    }

    List<ChannelStreamInfo> streams = new HlsStreamSegmentUrlProvider(null, null).loadChannelStreamsList(lines);
    return streams.isEmpty() ? null : streams.get(0).url;
  }

  private boolean isMasterPlaylistDirective(String lineText) {
    ExtendedM3uParser.Line line = ExtendedM3uParser.parseLine(lineText);

    return isStreamInfDirective(line) || isAudioMediaDirective(line);
  }

  private static boolean isStreamInfDirective(ExtendedM3uParser.Line line) {
    return line.isDirective() && EXT_X_STREAM_INF.equals(line.directiveName);
  }

  private static boolean isAudioMediaDirective(ExtendedM3uParser.Line line) {
    if (!line.isDirective() || !EXT_X_MEDIA.equals(line.directiveName)) {
      return false;
    }

    String type = getDirectiveArgument(line, "TYPE");
    String uri = getDirectiveArgument(line, "URI");

    return TYPE_AUDIO.equalsIgnoreCase(type) && !isBlank(uri);
  }

  private static String findAudioMediaPlaylistUrl(String[] lines) {
    String fallback = null;

    for (String lineText : lines) {
      ExtendedM3uParser.Line line = ExtendedM3uParser.parseLine(lineText);

      if (!isAudioMediaDirective(line)) {
        continue;
      }

      String uri = getDirectiveArgument(line, "URI");

      if (fallback == null) {
        fallback = uri;
      }

      String defaultValue = getDirectiveArgument(line, "DEFAULT");

      if ("YES".equalsIgnoreCase(defaultValue)) {
        return uri;
      }
    }

    return fallback;
  }

  private static String getDirectiveArgument(ExtendedM3uParser.Line line, String name) {
    Object value = line.directiveArguments.get(name.toUpperCase(Locale.ROOT));
    return value == null ? null : value.toString();
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
