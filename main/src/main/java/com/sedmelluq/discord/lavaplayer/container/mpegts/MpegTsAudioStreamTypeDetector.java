package com.sedmelluq.discord.lavaplayer.container.mpegts;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream.ADTS_ELEMENTARY_STREAM;
import static com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream.LATM_ELEMENTARY_STREAM;

/**
 * Small MPEG-TS PMT probe used before selecting the audio delegate for HLS streams.
 */
public final class MpegTsAudioStreamTypeDetector {
  private static final int TS_PACKET_SIZE = 188;
  private static final int PROBE_BYTES = TS_PACKET_SIZE * 256;

  private MpegTsAudioStreamTypeDetector() {
  }

  public static MpegTsAudioStreamType detect(InputStream inputStream) throws IOException {
    if (!inputStream.markSupported()) {
      inputStream = new BufferedInputStream(inputStream, PROBE_BYTES);
    }

    inputStream.mark(PROBE_BYTES);

    byte[] buffer = new byte[PROBE_BYTES];
    int length = 0;

    try {
      while (length < buffer.length) {
        int read = inputStream.read(buffer, length, buffer.length - length);

        if (read == -1) {
          break;
        }

        length += read;
      }
    } finally {
      inputStream.reset();
    }

    return detect(buffer, length);
  }

  public static MpegTsAudioStreamType detect(byte[] buffer, int length) {
    int syncOffset = findSyncOffset(buffer, length);

    if (syncOffset < 0) {
      return MpegTsAudioStreamType.UNKNOWN;
    }

    int pmtPid = -1;

    for (int offset = syncOffset; offset + TS_PACKET_SIZE <= length; offset += TS_PACKET_SIZE) {
      if ((buffer[offset] & 0xFF) != 0x47) {
        continue;
      }

      boolean payloadStart = (buffer[offset + 1] & 0x40) != 0;
      int pid = ((buffer[offset + 1] & 0x1F) << 8) | (buffer[offset + 2] & 0xFF);
      int payloadOffset = payloadOffset(buffer, offset);

      if (payloadOffset < 0 || payloadOffset >= offset + TS_PACKET_SIZE) {
        continue;
      }

      if (pid == 0 && payloadStart) {
        pmtPid = parsePat(buffer, payloadOffset, offset + TS_PACKET_SIZE);
      } else if (pmtPid >= 0 && pid == pmtPid && payloadStart) {
        MpegTsAudioStreamType result = parsePmt(buffer, payloadOffset, offset + TS_PACKET_SIZE);

        if (result != MpegTsAudioStreamType.UNKNOWN) {
          return result;
        }
      }
    }

    return MpegTsAudioStreamType.UNKNOWN;
  }

  private static int findSyncOffset(byte[] buffer, int length) {
    for (int offset = 0; offset < TS_PACKET_SIZE && offset + TS_PACKET_SIZE * 2 < length; offset++) {
      if ((buffer[offset] & 0xFF) == 0x47
          && (buffer[offset + TS_PACKET_SIZE] & 0xFF) == 0x47
          && (buffer[offset + TS_PACKET_SIZE * 2] & 0xFF) == 0x47) {
        return offset;
      }
    }

    return -1;
  }

  private static int payloadOffset(byte[] buffer, int packetOffset) {
    int adaptation = (buffer[packetOffset + 3] >> 4) & 0x03;

    if (adaptation == 0 || adaptation == 2) {
      return -1;
    }

    int offset = packetOffset + 4;

    if (adaptation == 3) {
      int adaptationLength = buffer[offset] & 0xFF;
      offset += 1 + adaptationLength;
    }

    return offset;
  }

  private static int parsePat(byte[] buffer, int offset, int packetEnd) {
    int pointerField = buffer[offset] & 0xFF;
    int position = offset + 1 + pointerField;

    if (position + 8 >= packetEnd || (buffer[position] & 0xFF) != 0x00) {
      return -1;
    }

    int sectionLength = ((buffer[position + 1] & 0x0F) << 8) | (buffer[position + 2] & 0xFF);
    int sectionEnd = Math.min(packetEnd, position + 3 + sectionLength - 4);
    position += 8;

    while (position + 4 <= sectionEnd) {
      int programNumber = ((buffer[position] & 0xFF) << 8) | (buffer[position + 1] & 0xFF);
      int pid = ((buffer[position + 2] & 0x1F) << 8) | (buffer[position + 3] & 0xFF);

      if (programNumber != 0) {
        return pid;
      }

      position += 4;
    }

    return -1;
  }

  private static MpegTsAudioStreamType parsePmt(byte[] buffer, int offset, int packetEnd) {
    int pointerField = buffer[offset] & 0xFF;
    int position = offset + 1 + pointerField;

    if (position + 12 >= packetEnd || (buffer[position] & 0xFF) != 0x02) {
      return MpegTsAudioStreamType.UNKNOWN;
    }

    int sectionLength = ((buffer[position + 1] & 0x0F) << 8) | (buffer[position + 2] & 0xFF);
    int sectionEnd = Math.min(packetEnd, position + 3 + sectionLength - 4);
    position += 8;

    position += 2; // PCR PID

    if (position + 2 > sectionEnd) {
      return MpegTsAudioStreamType.UNKNOWN;
    }

    int programInfoLength = ((buffer[position] & 0x0F) << 8) | (buffer[position + 1] & 0xFF);
    position += 2 + programInfoLength;

    while (position + 5 <= sectionEnd) {
      int streamType = buffer[position] & 0xFF;
      position += 3;

      int esInfoLength = ((buffer[position] & 0x0F) << 8) | (buffer[position + 1] & 0xFF);
      position += 2 + esInfoLength;

      if (streamType == ADTS_ELEMENTARY_STREAM) {
        return MpegTsAudioStreamType.ADTS_AAC;
      }

      if (streamType == LATM_ELEMENTARY_STREAM) {
        return MpegTsAudioStreamType.LATM_AAC;
      }
    }

    return MpegTsAudioStreamType.UNKNOWN;
  }
}
