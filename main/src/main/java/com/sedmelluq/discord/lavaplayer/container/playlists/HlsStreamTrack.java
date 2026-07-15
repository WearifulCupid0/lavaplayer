package com.sedmelluq.discord.lavaplayer.container.playlists;

import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream;
import com.sedmelluq.discord.lavaplayer.container.mpegts.PesPacketInputStream;
import com.sedmelluq.discord.lavaplayer.container.playlists.cmaf.CmafHlsAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamSegmentUrlProvider;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoProvider;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream.ADTS_ELEMENTARY_STREAM;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

/**
 * Audio track for HLS playlists.
 *
 * <p>Supported formats:</p>
 * <ul>
 *   <li>MPEG-TS segments containing ADTS/AAC.</li>
 *   <li>Direct ADTS/AAC segments.</li>
 *   <li>Direct MP3/MPEG audio segments.</li>
 *   <li>Unencrypted audio-only fragmented MP4/CMAF segments with AAC-LC.</li>
 * </ul>
 *
 * <p>Unsupported formats:</p>
 * <ul>
 *   <li>FairPlay/SAMPLE-AES/CENC/cbcs encrypted HLS.</li>
 *   <li>Video tracks or non-AAC CMAF audio tracks.</li>
 * </ul>
 */
public class HlsStreamTrack extends M3uStreamAudioTrack {
  private static final Logger log = LoggerFactory.getLogger(HlsStreamTrack.class);

  private static final int DETECTION_MARK_LIMIT = 65_536;
  private static final int DETECTION_SAMPLE_SIZE = 32_768;

  private final String streamUrl;
  private final boolean isInnerUrl;
  private final HlsStreamSegmentUrlProvider segmentUrlProvider;
  private final HttpInterfaceManager httpInterfaceManager;

  public HlsStreamTrack(AudioTrackInfo trackInfo, String streamUrl, HttpInterfaceManager httpInterfaceManager, boolean isInnerUrl) {
    super(trackInfo);

    this.streamUrl = streamUrl;
    this.isInnerUrl = isInnerUrl;
    this.segmentUrlProvider = isInnerUrl
        ? new HlsStreamSegmentUrlProvider(null, streamUrl)
        : new HlsStreamSegmentUrlProvider(streamUrl, null);
    this.httpInterfaceManager = httpInterfaceManager;
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
      log.debug("Detected HLS stream {} as fragmented MP4/CMAF segments.", getIdentifier());
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
      return;
    }

    throw new FriendlyException("Unsupported HLS stream format.", COMMON, null);
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

    int size = ((unsigned(sample[6]) & 0x7F) << 21)
        | ((unsigned(sample[7]) & 0x7F) << 14)
        | ((unsigned(sample[8]) & 0x7F) << 7)
        | (unsigned(sample[9]) & 0x7F);

    return 10 + size;
  }

  private static boolean looksLikeFragmentedMp4(byte[] sample, int offset, int length) {
    if (length - offset < 8) {
      return false;
    }

    return matchesAscii(sample, offset + 4, length, "ftyp")
        || matchesAscii(sample, offset + 4, length, "moof")
        || matchesAscii(sample, offset + 4, length, "styp");
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

  private static class NonSeekableStreamInputStream extends SeekableInputStream {
    private final InputStream inputStream;
    private long position;

    private NonSeekableStreamInputStream(InputStream inputStream) {
      super(Long.MAX_VALUE, 0);
      this.inputStream = inputStream;
    }

    @Override
    public int read() throws IOException {
      int result = inputStream.read();
      if (result >= 0) {
        position++;
      }
      return result;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int result = inputStream.read(buffer, offset, length);
      if (result > 0) {
        position += result;
      }
      return result;
    }

    @Override
    public long getPosition() {
      return position;
    }

    @Override
    public boolean canSeekHard() {
      return false;
    }

    @Override
    protected void seekHard(long position) throws IOException {
      throw new IOException("This stream is not seekable.");
    }

    @Override
    public List<AudioTrackInfoProvider> getTrackInfoProviders() {
      return Collections.emptyList();
    }
  }
}
