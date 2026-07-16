package com.sedmelluq.discord.lavaplayer.container.latm;

import java.io.EOFException;
import java.io.IOException;

public class LatmBitReader {
  private final byte[] data;
  private int bitPosition;

  public LatmBitReader(byte[] data) {
    this.data = data;
  }

  public boolean readBoolean() throws IOException {
    return readBits(1) != 0;
  }

  public int readBits(int bits) throws IOException {
    if (bits < 0 || bits > 31) {
      throw new IllegalArgumentException("Invalid bit count: " + bits);
    }

    if (bitsRemaining() < bits) {
      throw new EOFException("Not enough bits in LATM frame.");
    }

    int value = 0;

    for (int i = 0; i < bits; i++) {
      int byteIndex = bitPosition >> 3;
      int bitIndex = 7 - (bitPosition & 7);
      value = (value << 1) | ((data[byteIndex] >> bitIndex) & 1);
      bitPosition++;
    }

    return value;
  }

  public void skipBits(int bits) throws IOException {
    if (bitsRemaining() < bits) {
      throw new EOFException("Not enough bits in LATM frame.");
    }

    bitPosition += bits;
  }

  public byte[] readBytes(int length) throws IOException {
    byte[] result = new byte[length];

    for (int i = 0; i < length; i++) {
      result[i] = (byte) readBits(8);
    }

    return result;
  }

  public int bitsRemaining() {
    return data.length * 8 - bitPosition;
  }
}
