package com.sedmelluq.discord.lavaplayer.container.playlists;

import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream;
import com.sedmelluq.discord.lavaplayer.container.mpegts.PesPacketInputStream;
import com.sedmelluq.discord.lavaplayer.container.playlists.cmaf.CmafHlsAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamSegmentUrlProvider;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;

import static com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream.ADTS_ELEMENTARY_STREAM;
import static com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.fetchResponseLines;

/**
 * Audio track for HLS playlists.
 *
 * Supports:
 * - HLS with MPEG-TS segments containing ADTS/AAC.
 * - HLS with direct ADTS/AAC segments.
 * - HLS with direct MP3/MPEG audio segments.
 * - HLS with fragmented MP4/CMAF segments, including audio-only and video+audio variants.
 */
public class HlsStreamTrack extends M3uStreamAudioTrack {
  private static final Logger log = LoggerFactory.getLogger(HlsStreamTrack.class);

  private static final int DETECTION_MARK_LIMIT = 65_536;
  private static final int DETECTION_SAMPLE_SIZE = 32_768;

  private final HlsStreamSegmentUrlProvider segmentUrlProvider;
  private final HttpInterfaceManager httpInterfaceManager;
  private final String streamUrl;
  private final boolean isInnerUrl;

  public HlsStreamTrack(AudioTrackInfo trackInfo, String streamUrl, HttpInterfaceManager httpInterfaceManager, boolean isInnerUrl) {
    super(trackInfo);
    this.streamUrl = streamUrl;
    this.isInnerUrl = isInnerUrl;
    this.segmentUrlProvider = isInnerUrl
            ? new HlsStreamSegmentUrlProvider(null, streamUrl, trackInfo.isStream)
            : new HlsStreamSegmentUrlProvider(streamUrl, null, trackInfo.isStream);
    this.httpInterfaceManager = httpInterfaceManager;
  }

  @Override
  public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
    if (isLikelyCmafHls()) {
      log.debug("Detected HLS stream {} as fragmented MP4/CMAF before joining segments.", getIdentifier());
      processDelegate(new CmafHlsAudioTrack(trackInfo, streamUrl, httpInterfaceManager, isInnerUrl), localExecutor);
      return;
    }

    super.process(localExecutor);
  }

  @Override
  protected M3uStreamSegmentUrlProvider getSegmentUrlProvider() {
    return segmentUrlProvider;
  }

  @Override
  protected HttpInterface getHttpInterface() {
    return httpInterfaceManager.getInterface();
  }

  @Override
  protected void processJoinedStream(LocalAudioTrackExecutor localExecutor, InputStream stream) throws Exception {
    BufferedInputStream bufferedStream = stream instanceof BufferedInputStream
        ? (BufferedInputStream) stream
        : new BufferedInputStream(stream, DETECTION_MARK_LIMIT);

    SegmentFormat segmentFormat = detectSegmentFormat(bufferedStream);

    if (segmentFormat == SegmentFormat.ADTS) {
      log.debug("Detected HLS stream {} as direct ADTS/AAC segments.", getIdentifier());
      processDelegate(new AdtsAudioTrack(trackInfo, bufferedStream), localExecutor);
      return;
    }

    if (segmentFormat == SegmentFormat.MP3) {
      log.debug("Detected HLS stream {} as direct MP3/MPEG audio segments.", getIdentifier());
      processDelegate(new Mp3AudioTrack(trackInfo, new NonSeekableStreamInputStream(bufferedStream)), localExecutor);
      return;
    }

    if (segmentFormat == SegmentFormat.FRAGMENTED_MP4) {
      log.debug("Detected HLS stream {} as fragmented MP4/CMAF from first segment.", getIdentifier());
      processDelegate(new CmafHlsAudioTrack(trackInfo, streamUrl, httpInterfaceManager, isInnerUrl), localExecutor);
      return;
    }

    if (segmentFormat == SegmentFormat.MPEG_TS || segmentFormat == SegmentFormat.UNKNOWN) {
      if (segmentFormat == SegmentFormat.UNKNOWN) {
        log.debug("Could not detect HLS segment format for {}, falling back to MPEG-TS ADTS processing.", getIdentifier());
      } else {
        log.debug("Detected HLS stream {} as MPEG-TS ADTS segments.", getIdentifier());
      }

      MpegTsElementaryInputStream elementaryInputStream = new MpegTsElementaryInputStream(bufferedStream, ADTS_ELEMENTARY_STREAM);
      PesPacketInputStream pesPacketInputStream = new PesPacketInputStream(elementaryInputStream);
      processDelegate(new AdtsAudioTrack(trackInfo, pesPacketInputStream), localExecutor);
    }
  }

  private boolean isLikelyCmafHls() {
    try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
      String[] lines = fetchResponseLines(httpInterface, new HttpGet(streamUrl), "HLS stream list");

      if (containsCmafMediaTags(lines)) {
        return true;
      }

      if (isInnerUrl) {
        return false;
      }

      String mediaPlaylistUri = findPreferredMediaPlaylistUri(lines);

      if (mediaPlaylistUri == null) {
        return false;
      }

      String mediaPlaylistUrl = URI.create(streamUrl).resolve(mediaPlaylistUri).toString();
      String[] mediaLines = fetchResponseLines(httpInterface, new HttpGet(mediaPlaylistUrl), "HLS media playlist");

      return containsCmafMediaTags(mediaLines);
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean containsCmafMediaTags(String[] lines) {
    for (String lineText : lines) {
      ExtendedM3uParser.Line line = ExtendedM3uParser.parseLine(lineText);

      if (line.isDirective() && "EXT-X-MAP".equals(line.directiveName)) {
        return true;
      }

      if (line.isData()) {
        String lower = line.lineData.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".m4s") || lower.contains(".m4s?") || lower.contains("/fmp4/") || lower.endsWith(".mp4")) {
          return true;
        }
      }
    }

    return false;
  }

  private static String findPreferredMediaPlaylistUri(String[] lines) {
    String audioUri = findAudioMediaPlaylistUri(lines);

    if (audioUri != null) {
      return audioUri;
    }

    return findLowestBandwidthStreamUriWithAudio(lines);
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

      if (uri == null || uri.trim().isEmpty()) {
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

  private static String findLowestBandwidthStreamUriWithAudio(String[] lines) {
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

  private static String argument(ExtendedM3uParser.Line line, String name) {
    Object value = line.directiveArguments.get(name.toUpperCase(Locale.ROOT));
    return value == null ? null : value.toString();
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

  private static SegmentFormat detectSegmentFormat(BufferedInputStream inputStream) throws IOException {
    inputStream.mark(DETECTION_MARK_LIMIT);

    byte[] sample = new byte[DETECTION_SAMPLE_SIZE];
    int length;

    try {
      length = inputStream.read(sample);
    } finally {
      inputStream.reset();
    }

    if (length <= 0) {
      return SegmentFormat.UNKNOWN;
    }

    if (looksLikeMpegTs(sample, 0, length)) {
      return SegmentFormat.MPEG_TS;
    }

    SegmentFormat afterId3 = detectAfterOptionalId3(sample, length);

    if (afterId3 != SegmentFormat.UNKNOWN) {
      return afterId3;
    }

    if (looksLikeAdts(sample, 0, length)) {
      return SegmentFormat.ADTS;
    }

    if (looksLikeMp3(sample, 0, length)) {
      return SegmentFormat.MP3;
    }

    if (looksLikeFragmentedMp4(sample, 0, length)) {
      return SegmentFormat.FRAGMENTED_MP4;
    }

    return SegmentFormat.UNKNOWN;
  }

  private static SegmentFormat detectAfterOptionalId3(byte[] sample, int length) {
    if (!startsWithId3(sample, length)) {
      return SegmentFormat.UNKNOWN;
    }

    int offset = getId3TagSize(sample, length);

    if (offset <= 0 || offset >= length) {
      return SegmentFormat.UNKNOWN;
    }

    for (int i = offset; i < length - 4; i++) {
      if (looksLikeAdts(sample, i, length)) {
        return SegmentFormat.ADTS;
      }

      if (looksLikeMp3(sample, i, length)) {
        return SegmentFormat.MP3;
      }

      if (looksLikeMpegTs(sample, i, length)) {
        return SegmentFormat.MPEG_TS;
      }

      if (looksLikeFragmentedMp4(sample, i, length)) {
        return SegmentFormat.FRAGMENTED_MP4;
      }
    }

    return SegmentFormat.UNKNOWN;
  }

  private static boolean looksLikeMpegTs(byte[] sample, int offset, int length) {
    if (length - offset < 1 || unsigned(sample[offset]) != 0x47) {
      return false;
    }

    if (length - offset > 376) {
      return unsigned(sample[offset + 188]) == 0x47 && unsigned(sample[offset + 376]) == 0x47;
    }

    if (length - offset > 188) {
      return unsigned(sample[offset + 188]) == 0x47;
    }

    return true;
  }

  private static boolean looksLikeAdts(byte[] sample, int offset, int length) {
    if (length - offset < 2) {
      return false;
    }

    int first = unsigned(sample[offset]);
    int second = unsigned(sample[offset + 1]);

    return first == 0xFF && (second & 0xF0) == 0xF0;
  }

  private static boolean looksLikeMp3(byte[] sample, int offset, int length) {
    if (length - offset < 4) {
      return false;
    }

    int first = unsigned(sample[offset]);
    int second = unsigned(sample[offset + 1]);
    int third = unsigned(sample[offset + 2]);

    if (first != 0xFF || (second & 0xE0) != 0xE0) {
      return false;
    }

    int version = (second >> 3) & 0x03;
    int layer = (second >> 1) & 0x03;
    int bitrateIndex = (third >> 4) & 0x0F;
    int sampleRateIndex = (third >> 2) & 0x03;

    return version != 0x01 && layer != 0x00 && bitrateIndex != 0x00 && bitrateIndex != 0x0F && sampleRateIndex != 0x03;
  }

  private static boolean startsWithId3(byte[] sample, int length) {
    return length >= 10 && sample[0] == 'I' && sample[1] == 'D' && sample[2] == '3';
  }

  private static int getId3TagSize(byte[] sample, int length) {
    if (!startsWithId3(sample, length)) {
      return -1;
    }

    int size = ((unsigned(sample[6]) & 0x7F) << 21) |
        ((unsigned(sample[7]) & 0x7F) << 14) |
        ((unsigned(sample[8]) & 0x7F) << 7) |
        (unsigned(sample[9]) & 0x7F);

    return 10 + size;
  }

  private static boolean looksLikeFragmentedMp4(byte[] sample, int offset, int length) {
    if (length - offset < 8) {
      return false;
    }

    return matchesAscii(sample, offset + 4, length, "ftyp") ||
        matchesAscii(sample, offset + 4, length, "moof") ||
        matchesAscii(sample, offset + 4, length, "styp");
  }

  private static boolean matchesAscii(byte[] sample, int offset, int length, String value) {
    if (offset < 0 || length - offset < value.length()) {
      return false;
    }

    for (int i = 0; i < value.length(); i++) {
      if (sample[offset + i] != value.charAt(i)) {
        return false;
      }
    }

    return true;
  }

  private static int unsigned(byte value) {
    return value & 0xFF;
  }

  private enum SegmentFormat {
    MPEG_TS,
    ADTS,
    MP3,
    FRAGMENTED_MP4,
    UNKNOWN
  }
}
