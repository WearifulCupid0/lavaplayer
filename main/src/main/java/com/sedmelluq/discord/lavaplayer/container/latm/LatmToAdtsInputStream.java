package com.sedmelluq.discord.lavaplayer.container.latm;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Converts a simple LOAS/LATM AAC stream into ADTS frames.
 *
 * Supported subset:
 * - audioMuxVersion 0 or simple audioMuxVersion 1 config length wrapper.
 * - one program, one layer, one sub-frame.
 * - frameLengthType 0.
 */
public class LatmToAdtsInputStream extends InputStream {
  private static final int[] SAMPLE_RATES = new int[] {
      96000, 88200, 64000, 48000, 44100, 32000,
      24000, 22050, 16000, 12000, 11025, 8000,
      7350, -1, -1, -1
  };

  private final InputStream inputStream;

  private LatmAacConfig config;
  private byte[] currentFrame;
  private int currentFramePosition;
  private boolean endReached;

  public LatmToAdtsInputStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  @Override
  public int read() throws IOException {
    if (!ensureFrame()) {
      return -1;
    }

    return currentFrame[currentFramePosition++] & 0xFF;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    if (!ensureFrame()) {
      return -1;
    }

    int chunk = Math.min(length, currentFrame.length - currentFramePosition);
    System.arraycopy(currentFrame, currentFramePosition, buffer, offset, chunk);
    currentFramePosition += chunk;
    return chunk;
  }

  private boolean ensureFrame() throws IOException {
    if (currentFrame != null && currentFramePosition < currentFrame.length) {
      return true;
    }

    currentFrame = null;
    currentFramePosition = 0;

    if (endReached) {
      return false;
    }

    while (currentFrame == null) {
      byte[] muxElement = readLoasMuxElement();

      if (muxElement == null) {
        endReached = true;
        return false;
      }

      currentFrame = parseAudioMuxElement(muxElement);
    }

    return true;
  }

  private byte[] readLoasMuxElement() throws IOException {
    while (true) {
      int first = inputStream.read();

      if (first == -1) {
        return null;
      }

      if (first != 0x56) {
        continue;
      }

      int second = inputStream.read();

      if (second == -1) {
        return null;
      }

      if ((second & 0xE0) != 0xE0) {
        continue;
      }

      int third = inputStream.read();

      if (third == -1) {
        return null;
      }

      int length = ((second & 0x1F) << 8) | third;

      if (length <= 0) {
        continue;
      }

      byte[] data = new byte[length];
      readFully(data);
      return data;
    }
  }

  private void readFully(byte[] data) throws IOException {
    int position = 0;

    while (position < data.length) {
      int read = inputStream.read(data, position, data.length - position);

      if (read == -1) {
        throw new EOFException("Unexpected end of LOAS frame.");
      }

      position += read;
    }
  }

  private byte[] parseAudioMuxElement(byte[] muxElement) throws IOException {
    LatmBitReader reader = new LatmBitReader(muxElement);

    boolean useSameStreamMux = reader.readBoolean();

    if (!useSameStreamMux || config == null) {
      config = readStreamMuxConfig(reader);
    }

    if (config == null) {
      throw new IOException("LATM frame did not contain a StreamMuxConfig.");
    }

    int payloadLength = readPayloadLengthInfo(reader);

    if (payloadLength <= 0) {
      return null;
    }

    byte[] payload = reader.readBytes(payloadLength);
    byte[] header = new byte[7];
    AdtsHeaderWriter.write(header, config, payload.length);

    byte[] frame = new byte[header.length + payload.length];
    System.arraycopy(header, 0, frame, 0, header.length);
    System.arraycopy(payload, 0, frame, header.length, payload.length);
    return frame;
  }

  private LatmAacConfig readStreamMuxConfig(LatmBitReader reader) throws IOException {
    int audioMuxVersion = reader.readBits(1);
    int audioMuxVersionA = 0;

    if (audioMuxVersion == 1) {
      audioMuxVersionA = reader.readBits(1);
    }

    if (audioMuxVersionA != 0) {
      throw new IOException("Unsupported LATM audioMuxVersionA: " + audioMuxVersionA);
    }

    if (audioMuxVersion == 1) {
      readLatmValue(reader);
    }

    boolean allStreamsSameTimeFraming = reader.readBoolean();
    int numSubFrames = reader.readBits(6);
    int numProgram = reader.readBits(4);
    int numLayer = reader.readBits(3);

    if (!allStreamsSameTimeFraming || numSubFrames != 0 || numProgram != 0 || numLayer != 0) {
      throw new IOException("Unsupported LATM stream layout.");
    }

    LatmAacConfig parsedConfig;

    if (audioMuxVersion == 0) {
      parsedConfig = readAudioSpecificConfig(reader);
    } else {
      int ascLen = (int) readLatmValue(reader);
      int startBits = reader.bitsRemaining();
      parsedConfig = readAudioSpecificConfig(reader);
      int consumed = startBits - reader.bitsRemaining();
      int remaining = ascLen - consumed;

      if (remaining > 0) {
        reader.skipBits(remaining);
      }
    }

    int frameLengthType = reader.readBits(3);

    if (frameLengthType != 0) {
      throw new IOException("Unsupported LATM frameLengthType: " + frameLengthType);
    }

    reader.skipBits(8); // latmBufferFullness

    boolean otherDataPresent = reader.readBoolean();

    if (otherDataPresent) {
      if (audioMuxVersion == 1) {
        long otherDataLenBits = readLatmValue(reader);
        reader.skipBits((int) otherDataLenBits);
      } else {
        boolean otherDataLenEsc;
        do {
          otherDataLenEsc = reader.readBoolean();
          reader.skipBits(8);
        } while (otherDataLenEsc);
      }
    }

    boolean crcCheckPresent = reader.readBoolean();

    if (crcCheckPresent) {
      reader.skipBits(8);
    }

    return parsedConfig;
  }

  private LatmAacConfig readAudioSpecificConfig(LatmBitReader reader) throws IOException {
    int audioObjectType = readAudioObjectType(reader);
    int samplingFrequencyIndex = reader.readBits(4);
    int sampleRate;

    if (samplingFrequencyIndex == 0x0F) {
      sampleRate = reader.readBits(24);
    } else {
      sampleRate = SAMPLE_RATES[samplingFrequencyIndex];
    }

    int channelConfiguration = reader.readBits(4);

    if (audioObjectType == 5 || audioObjectType == 29) {
      int extensionSamplingFrequencyIndex = reader.readBits(4);

      if (extensionSamplingFrequencyIndex == 0x0F) {
        reader.skipBits(24);
      }

      audioObjectType = readAudioObjectType(reader);
    }

    if (sampleRate <= 0 || channelConfiguration <= 0) {
      throw new IOException("Unsupported AAC config in LATM stream.");
    }

    return new LatmAacConfig(audioObjectType, samplingFrequencyIndex, sampleRate, channelConfiguration);
  }

  private int readAudioObjectType(LatmBitReader reader) throws IOException {
    int audioObjectType = reader.readBits(5);

    if (audioObjectType == 31) {
      audioObjectType = 32 + reader.readBits(6);
    }

    return audioObjectType;
  }

  private int readPayloadLengthInfo(LatmBitReader reader) throws IOException {
    int muxSlotLengthBytes = 0;
    int tmp;

    do {
      tmp = reader.readBits(8);
      muxSlotLengthBytes += tmp;
    } while (tmp == 255);

    return muxSlotLengthBytes;
  }

  private long readLatmValue(LatmBitReader reader) throws IOException {
    int bytesForValue = reader.readBits(2);
    long value = 0;

    for (int i = 0; i <= bytesForValue; i++) {
      value = (value << 8) | reader.readBits(8);
    }

    return value;
  }
}
