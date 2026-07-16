package com.sedmelluq.discord.lavaplayer.container.playlists.cmaf;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

public final class FragmentedMp4Demuxer {
  private FragmentedMp4Demuxer() {
  }

  /**
   * Parses an fMP4 init segment and returns AAC config for the audio track only.
   *
   * This version supports multiplexed fMP4 files, like Dailymotion VOD HLS variants,
   * where the init segment contains both a video track and an audio track. The audio
   * track is selected from mdia/hdlr == "soun" and its track id is later used to ignore
   * video traf boxes in media fragments.
   *
   * NOTE: It should be mentioned that it only has been tests with Mixcloud lives,
   * that returns audio-only streams, and Dailymotion videos with video + audio streams,
   * if a source uses a different model from Mixcloud or Dailymotion this might break
   */
  public static AacAudioConfig parseAacConfig(byte[] initSegment) {
    List<Mp4Box> topLevel = readBoxes(initSegment, 0, initSegment.length);
    Mp4Box moov = findBox(topLevel, "moov", 0);

    if (moov == null) {
      return parseLegacyAacConfig(initSegment);
    }

    List<Mp4Box> moovChildren = readBoxes(initSegment, moov.payloadStart(), moov.end());
    Map<Integer, AacAudioConfig.TrackDefaults> defaults = parseTrexDefaults(initSegment, moovChildren);

    for (Mp4Box trak : findBoxes(moovChildren, "trak")) {
      int trackId = parseTrackId(initSegment, trak);
      String handlerType = parseHandlerType(initSegment, trak);

      if (!"soun".equals(handlerType)) {
        continue;
      }

      byte[] esdsPayload = findBoxPayloadByType(initSegment, trak.payloadStart(), trak.end(), "esds");

      if (esdsPayload == null) {
        throw new FriendlyException("CMAF audio track does not contain an AAC esds box.", COMMON, null);
      }

      return buildAacConfig(esdsPayload, trackId, defaults);
    }

    throw new FriendlyException("CMAF init segment does not contain an AAC audio track.", COMMON, null);
  }

  public static byte[] demuxToAdts(byte[] fragment, AacAudioConfig config) {
    List<Mp4Box> topLevel = readBoxes(fragment, 0, fragment.length);
    ByteArrayOutputStream output = new ByteArrayOutputStream(fragment.length + 4096);

    int searchStart = 0;

    while (true) {
      Mp4Box moof = findBox(topLevel, "moof", searchStart);

      if (moof == null) {
        break;
      }

      Mp4Box mdat = findNextBox(topLevel, "mdat", moof.end());

      if (mdat == null) {
        break;
      }

      List<MediaRun> runs = parseMoof(fragment, moof, config);
      int implicitDataStart = mdat.payloadStart();

      for (MediaRun run : runs) {
        int dataStart = run.dataOffset != null ? safeInt(moof.start + run.dataOffset) : implicitDataStart;
        int totalRunSize = run.totalSize();

        if (dataStart < mdat.payloadStart() || dataStart > mdat.end()) {
          dataStart = implicitDataStart;
        }

        if (run.trackId == config.trackId || config.trackId < 0) {
          writeAudioRun(fragment, output, config, mdat, dataStart, run.sampleSizes);
        }

        implicitDataStart = Math.max(implicitDataStart, dataStart + totalRunSize);
      }

      searchStart = mdat.end();
    }

    return output.toByteArray();
  }

  private static void writeAudioRun(
      byte[] fragment,
      ByteArrayOutputStream output,
      AacAudioConfig config,
      Mp4Box mdat,
      int dataStart,
      List<Integer> sampleSizes
  ) {
    for (Integer sampleSize : sampleSizes) {
      if (sampleSize == null || sampleSize <= 0) {
        continue;
      }

      int sampleEnd = dataStart + sampleSize;

      if (sampleEnd > mdat.end() || sampleEnd > fragment.length) {
        break;
      }

      output.write(config.createAdtsHeader(sampleSize), 0, 7);
      output.write(fragment, dataStart, sampleSize);
      dataStart = sampleEnd;
    }
  }

  private static List<MediaRun> parseMoof(byte[] data, Mp4Box moof, AacAudioConfig config) {
    List<Mp4Box> moofChildren = readBoxes(data, moof.payloadStart(), moof.end());
    List<MediaRun> runs = new ArrayList<>();

    for (Mp4Box traf : findBoxes(moofChildren, "traf")) {
      List<Mp4Box> trafChildren = readBoxes(data, traf.payloadStart(), traf.end());
      Mp4Box tfhd = findBox(trafChildren, "tfhd", 0);

      if (tfhd == null) {
        continue;
      }

      TfhdInfo tfhdInfo = parseTfhd(data, tfhd, config);

      for (Mp4Box trun : findBoxes(trafChildren, "trun")) {
        runs.add(parseTrun(data, trun, tfhdInfo));
      }
    }

    if (runs.isEmpty()) {
      throw new FriendlyException("CMAF fragment moof does not contain usable traf/trun boxes.", COMMON, null);
    }

    return runs;
  }

  private static TfhdInfo parseTfhd(byte[] data, Mp4Box tfhd, AacAudioConfig config) {
    int offset = tfhd.payloadStart();
    int flags = readUInt24(data, offset + 1);
    offset += 4;

    int trackId = safeInt(readUInt32(data, offset));
    offset += 4;

    AacAudioConfig.TrackDefaults trexDefaults = config.defaultsForTrack(trackId);

    long defaultSampleDuration = trexDefaults.defaultSampleDuration;
    long defaultSampleSize = trexDefaults.defaultSampleSize;
    long defaultSampleFlags = trexDefaults.defaultSampleFlags;

    if ((flags & 0x000001) != 0) {
      offset += 8; // base_data_offset
    }

    if ((flags & 0x000002) != 0) {
      offset += 4; // sample_description_index
    }

    if ((flags & 0x000008) != 0) {
      defaultSampleDuration = readUInt32(data, offset);
      offset += 4;
    }

    if ((flags & 0x000010) != 0) {
      defaultSampleSize = readUInt32(data, offset);
      offset += 4;
    }

    if ((flags & 0x000020) != 0) {
      defaultSampleFlags = readUInt32(data, offset);
    }

    return new TfhdInfo(trackId, defaultSampleDuration, defaultSampleSize, defaultSampleFlags);
  }

  private static MediaRun parseTrun(byte[] data, Mp4Box trun, TfhdInfo tfhd) {
    int offset = trun.payloadStart();
    int flags = readUInt24(data, offset + 1);
    offset += 4;

    int sampleCount = safeInt(readUInt32(data, offset));
    offset += 4;

    Integer dataOffset = null;

    if ((flags & 0x000001) != 0) {
      dataOffset = readInt32(data, offset);
      offset += 4;
    }

    if ((flags & 0x000004) != 0) {
      offset += 4; // first_sample_flags
    }

    boolean hasSampleDuration = (flags & 0x000100) != 0;
    boolean hasSampleSize = (flags & 0x000200) != 0;
    boolean hasSampleFlags = (flags & 0x000400) != 0;
    boolean hasCompositionTimeOffset = (flags & 0x000800) != 0;

    List<Integer> sampleSizes = new ArrayList<>(sampleCount);

    for (int i = 0; i < sampleCount; i++) {
      if (hasSampleDuration) {
        offset += 4;
      }

      int sampleSize;

      if (hasSampleSize) {
        sampleSize = safeInt(readUInt32(data, offset));
        offset += 4;
      } else {
        sampleSize = safeInt(tfhd.defaultSampleSize);
      }

      if (hasSampleFlags) {
        offset += 4;
      }

      if (hasCompositionTimeOffset) {
        offset += 4;
      }

      sampleSizes.add(sampleSize);
    }

    return new MediaRun(tfhd.trackId, dataOffset, sampleSizes);
  }

  private static AacAudioConfig parseLegacyAacConfig(byte[] initSegment) {
    byte[] esdsPayload = findBoxPayloadByType(initSegment, 0, initSegment.length, "esds");

    if (esdsPayload == null) {
      throw new FriendlyException("CMAF init segment does not contain an AAC esds box.", COMMON, null);
    }

    return buildAacConfig(esdsPayload, -1, new HashMap<>());
  }

  private static AacAudioConfig buildAacConfig(
      byte[] esdsPayload,
      int trackId,
      Map<Integer, AacAudioConfig.TrackDefaults> defaults
  ) {
    byte[] audioSpecificConfig = findDecoderSpecificInfo(esdsPayload);

    if (audioSpecificConfig == null || audioSpecificConfig.length < 2) {
      throw new FriendlyException("CMAF init segment does not contain AAC AudioSpecificConfig.", COMMON, null);
    }

    BitReader bits = new BitReader(audioSpecificConfig);
    int audioObjectType = bits.readAudioObjectType();
    int sampleRateIndex = bits.readBits(4);

    if (sampleRateIndex == 0x0F) {
      throw new FriendlyException("AAC streams with explicit sample rate are not supported in CMAF HLS yet.", COMMON, null);
    }

    int channelConfig = bits.readBits(4);

    if (channelConfig == 0) {
      throw new FriendlyException("AAC Program Config Element is not supported in CMAF HLS yet.", COMMON, null);
    }

    if (audioObjectType != 2 && audioObjectType != 5 && audioObjectType != 29) {
      throw new FriendlyException("Only AAC-LC/HE-AAC CMAF HLS streams are supported. Audio object type: " + audioObjectType, COMMON, null);
    }

    // For HE-AAC/SBR, keep ADTS as AAC-LC for the native AAC decoder pipeline.
    int adtsObjectType = 2;

    return new AacAudioConfig(adtsObjectType, sampleRateIndex, channelConfig, trackId, defaults);
  }

  private static Map<Integer, AacAudioConfig.TrackDefaults> parseTrexDefaults(byte[] data, List<Mp4Box> moovChildren) {
    Map<Integer, AacAudioConfig.TrackDefaults> defaults = new HashMap<>();
    Mp4Box mvex = findBox(moovChildren, "mvex", 0);

    if (mvex == null) {
      return defaults;
    }

    for (Mp4Box trex : findBoxes(readBoxes(data, mvex.payloadStart(), mvex.end()), "trex")) {
      int offset = trex.payloadStart() + 4; // full-box version/flags
      int trackId = safeInt(readUInt32(data, offset));
      offset += 4;
      offset += 4; // default_sample_description_index
      long defaultSampleDuration = readUInt32(data, offset);
      offset += 4;
      long defaultSampleSize = readUInt32(data, offset);
      offset += 4;
      long defaultSampleFlags = readUInt32(data, offset);

      defaults.put(trackId, new AacAudioConfig.TrackDefaults(defaultSampleDuration, defaultSampleSize, defaultSampleFlags));
    }

    return defaults;
  }

  private static int parseTrackId(byte[] data, Mp4Box trak) {
    Mp4Box tkhd = findBox(readBoxes(data, trak.payloadStart(), trak.end()), "tkhd", 0);

    if (tkhd == null) {
      return -1;
    }

    int version = data[tkhd.payloadStart()] & 0xFF;
    int offset = tkhd.payloadStart() + 4;

    if (version == 1) {
      offset += 8 + 8;
    } else {
      offset += 4 + 4;
    }

    return safeInt(readUInt32(data, offset));
  }

  private static String parseHandlerType(byte[] data, Mp4Box trak) {
    Mp4Box mdia = findBox(readBoxes(data, trak.payloadStart(), trak.end()), "mdia", 0);

    if (mdia == null) {
      return null;
    }

    Mp4Box hdlr = findBox(readBoxes(data, mdia.payloadStart(), mdia.end()), "hdlr", 0);

    if (hdlr == null || hdlr.payloadStart() + 12 > hdlr.end()) {
      return null;
    }

    // hdlr is a full box: version/flags(4), pre_defined(4), handler_type(4)
    return readAscii(data, hdlr.payloadStart() + 8, 4);
  }

  private static byte[] findBoxPayloadByType(byte[] data, int start, int end, String type) {
    int index = start;

    while (index + 8 <= end && index + 8 <= data.length) {
      int typeOffset = indexOfAscii(data, type, index + 4, end);

      if (typeOffset < 4) {
        return null;
      }

      int boxStart = typeOffset - 4;
      long boxSize = readUInt32(data, boxStart);
      int headerSize = 8;

      if (boxSize == 1) {
        if (boxStart + 16 > end || boxStart + 16 > data.length) {
          return null;
        }

        boxSize = readUInt64AsLong(data, boxStart + 8);
        headerSize = 16;
      }

      long boxEnd = boxStart + boxSize;

      if (boxSize >= headerSize && boxEnd <= end && boxEnd <= data.length) {
        int payloadStart = boxStart + headerSize;
        int payloadLength = (int) (boxEnd - payloadStart);
        byte[] payload = new byte[payloadLength];
        System.arraycopy(data, payloadStart, payload, 0, payloadLength);
        return payload;
      }

      index = typeOffset + 4;
    }

    return null;
  }

  private static byte[] findDecoderSpecificInfo(byte[] esdsPayload) {
    int start = esdsPayload.length >= 4 ? 4 : 0; // skip full-box version/flags when present

    for (int i = start; i < esdsPayload.length - 2; i++) {
      if ((esdsPayload[i] & 0xFF) != 0x05) {
        continue;
      }

      DescriptorSize size = readDescriptorSize(esdsPayload, i + 1);

      if (size == null) {
        continue;
      }

      int payloadStart = i + 1 + size.bytesRead;
      int payloadEnd = payloadStart + size.size;

      if (payloadStart >= 0 && payloadEnd <= esdsPayload.length && size.size > 0) {
        byte[] result = new byte[size.size];
        System.arraycopy(esdsPayload, payloadStart, result, 0, size.size);
        return result;
      }
    }

    return null;
  }

  private static List<Mp4Box> readBoxes(byte[] data, int start, int end) {
    List<Mp4Box> boxes = new ArrayList<>();
    int offset = start;

    while (offset + 8 <= end && offset + 8 <= data.length) {
      long size = readUInt32(data, offset);
      String type = readAscii(data, offset + 4, 4);
      int headerSize = 8;

      if (size == 1) {
        if (offset + 16 > end || offset + 16 > data.length) {
          break;
        }

        size = readUInt64AsLong(data, offset + 8);
        headerSize = 16;
      } else if (size == 0) {
        size = end - offset;
      }

      if (size < headerSize || offset + size > end || offset + size > data.length) {
        break;
      }

      boxes.add(new Mp4Box(offset, headerSize, size, type));
      offset += (int) size;
    }

    return boxes;
  }

  private static Mp4Box findBox(List<Mp4Box> boxes, String type, int afterOffset) {
    for (Mp4Box box : boxes) {
      if (box.start >= afterOffset && type.equals(box.type)) {
        return box;
      }
    }

    return null;
  }

  private static List<Mp4Box> findBoxes(List<Mp4Box> boxes, String type) {
    List<Mp4Box> matches = new ArrayList<>();

    for (Mp4Box box : boxes) {
      if (type.equals(box.type)) {
        matches.add(box);
      }
    }

    return matches;
  }

  private static Mp4Box findNextBox(List<Mp4Box> boxes, String type, int afterOffset) {
    for (Mp4Box box : boxes) {
      if (box.start >= afterOffset && type.equals(box.type)) {
        return box;
      }
    }

    return null;
  }

  private static int indexOfAscii(byte[] data, String value, int start, int end) {
    int limit = Math.min(end, data.length) - value.length();

    outer:
    for (int i = Math.max(0, start); i <= limit; i++) {
      for (int j = 0; j < value.length(); j++) {
        if (data[i + j] != value.charAt(j)) {
          continue outer;
        }
      }

      return i;
    }

    return -1;
  }

  private static String readAscii(byte[] data, int offset, int length) {
    StringBuilder builder = new StringBuilder(length);

    for (int i = 0; i < length; i++) {
      builder.append((char) data[offset + i]);
    }

    return builder.toString();
  }

  private static int readUInt24(byte[] data, int offset) {
    return ((data[offset] & 0xFF) << 16) | ((data[offset + 1] & 0xFF) << 8) | (data[offset + 2] & 0xFF);
  }

  private static int readInt32(byte[] data, int offset) {
    return (data[offset] << 24) | ((data[offset + 1] & 0xFF) << 16) | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
  }

  private static long readUInt32(byte[] data, int offset) {
    return ((long) (data[offset] & 0xFF) << 24) | ((long) (data[offset + 1] & 0xFF) << 16) |
        ((long) (data[offset + 2] & 0xFF) << 8) | (long) (data[offset + 3] & 0xFF);
  }

  private static long readUInt64AsLong(byte[] data, int offset) {
    long high = readUInt32(data, offset);
    long low = readUInt32(data, offset + 4);

    if (high > Integer.MAX_VALUE) {
      throw new FriendlyException("MP4 box is too large to process in memory.", COMMON, null);
    }

    return (high << 32) | low;
  }

  private static int safeInt(long value) {
    if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
      throw new FriendlyException("MP4 value is too large to process in memory.", COMMON, null);
    }

    return (int) value;
  }

  private static DescriptorSize readDescriptorSize(byte[] data, int offset) {
    int size = 0;
    int bytesRead = 0;

    for (int i = 0; i < 4 && offset + i < data.length; i++) {
      int value = data[offset + i] & 0xFF;
      bytesRead++;
      size = (size << 7) | (value & 0x7F);

      if ((value & 0x80) == 0) {
        return new DescriptorSize(size, bytesRead);
      }
    }

    return null;
  }

  private static class TfhdInfo {
    private final int trackId;
    private final long defaultSampleDuration;
    private final long defaultSampleSize;
    @SuppressWarnings("unused")
    private final long defaultSampleFlags;

    private TfhdInfo(int trackId, long defaultSampleDuration, long defaultSampleSize, long defaultSampleFlags) {
      this.trackId = trackId;
      this.defaultSampleDuration = defaultSampleDuration;
      this.defaultSampleSize = defaultSampleSize;
      this.defaultSampleFlags = defaultSampleFlags;
    }
  }

  private static class MediaRun {
    private final int trackId;
    private final Integer dataOffset;
    private final List<Integer> sampleSizes;

    private MediaRun(int trackId, Integer dataOffset, List<Integer> sampleSizes) {
      this.trackId = trackId;
      this.dataOffset = dataOffset;
      this.sampleSizes = sampleSizes;
    }

    private int totalSize() {
      int total = 0;

      for (Integer sampleSize : sampleSizes) {
        if (sampleSize != null && sampleSize > 0) {
          total += sampleSize;
        }
      }

      return total;
    }
  }

  private static class DescriptorSize {
    private final int size;
    private final int bytesRead;

    private DescriptorSize(int size, int bytesRead) {
      this.size = size;
      this.bytesRead = bytesRead;
    }
  }

  private static class BitReader {
    private final byte[] data;
    private int bitOffset;

    private BitReader(byte[] data) {
      this.data = data;
    }

    private int readAudioObjectType() {
      int audioObjectType = readBits(5);

      if (audioObjectType == 31) {
        audioObjectType = 32 + readBits(6);
      }

      return audioObjectType;
    }

    private int readBits(int count) {
      int result = 0;

      for (int i = 0; i < count; i++) {
        int byteIndex = bitOffset / 8;
        int bitIndex = 7 - (bitOffset % 8);
        bitOffset++;

        if (byteIndex >= data.length) {
          return result;
        }

        result = (result << 1) | ((data[byteIndex] >> bitIndex) & 0x01);
      }

      return result;
    }
  }
}
