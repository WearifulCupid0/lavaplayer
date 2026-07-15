package com.sedmelluq.discord.lavaplayer.container.playlists.cmaf;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

public final class FragmentedMp4Demuxer {
  private FragmentedMp4Demuxer() {
  }

  public static AacAudioConfig parseAacConfig(byte[] initSegment) {
    byte[] esdsPayload = findBoxPayloadByType(initSegment, "esds");

    if (esdsPayload == null) {
      throw new FriendlyException("CMAF init segment does not contain an AAC esds box.", COMMON, null);
    }

    byte[] audioSpecificConfig = findDecoderSpecificInfo(esdsPayload);

    if (audioSpecificConfig == null || audioSpecificConfig.length < 2) {
      throw new FriendlyException("CMAF init segment does not contain AAC AudioSpecificConfig.", COMMON, null);
    }

    BitReader bits = new BitReader(audioSpecificConfig);
    int audioObjectType = bits.readBits(5);

    if (audioObjectType == 31) {
      audioObjectType = 32 + bits.readBits(6);
    }

    int sampleRateIndex = bits.readBits(4);

    if (sampleRateIndex == 0x0F) {
      // Explicit sample rate. ADTS cannot encode arbitrary sample rate directly, so reject for now.
      throw new FriendlyException("AAC streams with explicit sample rate are not supported in CMAF HLS yet.", COMMON, null);
    }

    int channelConfig = bits.readBits(4);

    if (channelConfig == 0) {
      throw new FriendlyException("AAC Program Config Element is not supported in CMAF HLS yet.", COMMON, null);
    }

    if (audioObjectType != 2 && audioObjectType != 5 && audioObjectType != 29) {
      throw new FriendlyException("Only AAC-LC/HE-AAC CMAF HLS streams are supported. Audio object type: " + audioObjectType, COMMON, null);
    }

    // For HE-AAC/SBR, ADTS should still signal the underlying AAC-LC decoder config to the native AAC decoder.
    int adtsObjectType = 2;
    return new AacAudioConfig(adtsObjectType, sampleRateIndex, channelConfig);
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

      FragmentInfo fragmentInfo = parseMoof(fragment, moof);
      if (fragmentInfo.sampleSizes.isEmpty()) {
        throw new FriendlyException("CMAF fragment does not contain sample sizes in trun/tfhd.", COMMON, null);
      }

      int dataStart = fragmentInfo.dataOffset != null
          ? safeInt(moof.start + fragmentInfo.dataOffset)
          : mdat.payloadStart();

      if (dataStart < mdat.payloadStart() || dataStart >= mdat.end()) {
        dataStart = mdat.payloadStart();
      }

      for (Integer sampleSize : fragmentInfo.sampleSizes) {
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

      searchStart = mdat.end();
    }

    return output.toByteArray();
  }

  private static FragmentInfo parseMoof(byte[] data, Mp4Box moof) {
    List<Mp4Box> moofChildren = readBoxes(data, moof.payloadStart(), moof.end());
    Mp4Box traf = findBox(moofChildren, "traf", 0);

    if (traf == null) {
      throw new FriendlyException("CMAF fragment moof does not contain traf.", COMMON, null);
    }

    List<Mp4Box> trafChildren = readBoxes(data, traf.payloadStart(), traf.end());
    Mp4Box tfhd = findBox(trafChildren, "tfhd", 0);
    Mp4Box trun = findBox(trafChildren, "trun", 0);

    if (trun == null) {
      throw new FriendlyException("CMAF fragment traf does not contain trun.", COMMON, null);
    }

    long defaultSampleSize = tfhd == null ? 0L : parseTfhdDefaultSampleSize(data, tfhd);
    return parseTrun(data, trun, defaultSampleSize);
  }

  private static long parseTfhdDefaultSampleSize(byte[] data, Mp4Box tfhd) {
    int offset = tfhd.payloadStart();
    int flags = readUInt24(data, offset + 1);
    offset += 4;

    // track_ID
    offset += 4;

    if ((flags & 0x000001) != 0) {
      offset += 8;
    }
    if ((flags & 0x000002) != 0) {
      offset += 4;
    }
    if ((flags & 0x000008) != 0) {
      offset += 4;
    }
    if ((flags & 0x000010) != 0) {
      return readUInt32(data, offset);
    }

    return 0L;
  }

  private static FragmentInfo parseTrun(byte[] data, Mp4Box trun, long defaultSampleSize) {
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
      offset += 4;
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
        sampleSize = safeInt(defaultSampleSize);
      }

      if (hasSampleFlags) {
        offset += 4;
      }
      if (hasCompositionTimeOffset) {
        offset += 4;
      }

      sampleSizes.add(sampleSize);
    }

    return new FragmentInfo(dataOffset, sampleSizes);
  }

  private static byte[] findBoxPayloadByType(byte[] data, String type) {
    int index = 0;
    while (index + 8 <= data.length) {
      int typeOffset = indexOfAscii(data, type, index + 4);
      if (typeOffset < 4) {
        return null;
      }

      int boxStart = typeOffset - 4;
      long boxSize = readUInt32(data, boxStart);
      int headerSize = 8;

      if (boxSize == 1) {
        if (boxStart + 16 > data.length) {
          return null;
        }
        boxSize = readUInt64AsLong(data, boxStart + 8);
        headerSize = 16;
      }

      long boxEnd = boxStart + boxSize;
      if (boxSize >= headerSize && boxEnd <= data.length) {
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

  private static Mp4Box findNextBox(List<Mp4Box> boxes, String type, int afterOffset) {
    for (Mp4Box box : boxes) {
      if (box.start >= afterOffset && type.equals(box.type)) {
        return box;
      }
    }

    return null;
  }

  private static int indexOfAscii(byte[] data, String value, int start) {
    outer:
    for (int i = Math.max(0, start); i <= data.length - value.length(); i++) {
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
    return ((long) (data[offset] & 0xFF) << 24)
        | ((long) (data[offset + 1] & 0xFF) << 16)
        | ((long) (data[offset + 2] & 0xFF) << 8)
        | (long) (data[offset + 3] & 0xFF);
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
    if (value > Integer.MAX_VALUE) {
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

  private static class FragmentInfo {
    private final Integer dataOffset;
    private final List<Integer> sampleSizes;

    private FragmentInfo(Integer dataOffset, List<Integer> sampleSizes) {
      this.dataOffset = dataOffset;
      this.sampleSizes = sampleSizes;
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
