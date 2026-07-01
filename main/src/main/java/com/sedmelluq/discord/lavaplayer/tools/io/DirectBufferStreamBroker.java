package com.sedmelluq.discord.lavaplayer.tools.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * A helper class to consume the entire contents of a stream into a direct byte buffer.
 * Designed for cases where this is repeated several times, as it supports resetting.
 */
public class DirectBufferStreamBroker {
  private static final int COPY_BUFFER_SIZE = 8192;

  private final byte[] copyBuffer;
  private final int initialSize;

  private int readByteCount;
  private ByteBuffer currentBuffer;

  public DirectBufferStreamBroker(int initialSize) {
    this.initialSize = initialSize;
    this.copyBuffer = new byte[COPY_BUFFER_SIZE];
    this.currentBuffer = ByteBuffer.allocateDirect(initialSize);
  }

  public void resetAndCompact() {
    if (currentBuffer.capacity() == initialSize) {
      currentBuffer.clear();
    } else {
      currentBuffer = ByteBuffer.allocateDirect(initialSize);
    }

    readByteCount = 0;
  }

  public void clear() {
    currentBuffer.clear();
    readByteCount = 0;
  }

  public ByteBuffer getBuffer() {
    ByteBuffer buffer = currentBuffer.duplicate();
    buffer.flip();
    return buffer;
  }

  public boolean isTruncated() {
    return currentBuffer.position() < readByteCount;
  }

  public byte[] extractBytes() {
    byte[] data = new byte[currentBuffer.position()];
    currentBuffer.position(0);
    currentBuffer.get(data, 0, data.length);
    return data;
  }

  public boolean consumeNext(InputStream inputStream, int maximumSavedBytes, int maximumReadBytes)
          throws IOException {
    currentBuffer.clear();
    readByteCount = 0;

    int available = Math.max(0, inputStream.available());
    ensureCapacity(Math.min(maximumSavedBytes, available));

    while (readByteCount < maximumReadBytes) {
      int maximumReadFragment = Math.min(copyBuffer.length, maximumReadBytes - readByteCount);
      int fragmentLength = inputStream.read(copyBuffer, 0, maximumReadFragment);

      if (fragmentLength == -1) {
        return true;
      }

      int remainingSavedBytes = Math.max(0, maximumSavedBytes - readByteCount);
      int bytesToSave = Math.min(fragmentLength, remainingSavedBytes);

      if (bytesToSave > 0) {
        ensureCapacity(currentBuffer.position() + bytesToSave);
        currentBuffer.put(copyBuffer, 0, bytesToSave);
      }

      readByteCount += fragmentLength;
    }

    return false;
  }

  private void ensureCapacity(int capacity) {
    if (capacity <= currentBuffer.capacity()) {
      return;
    }

    int newCapacity = currentBuffer.capacity();

    while (newCapacity < capacity) {
      newCapacity <<= 1;
    }

    ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity);
    currentBuffer.flip();
    newBuffer.put(currentBuffer);
    currentBuffer = newBuffer;
  }
}