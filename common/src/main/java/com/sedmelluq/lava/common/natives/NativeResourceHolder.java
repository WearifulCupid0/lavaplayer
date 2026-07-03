package com.sedmelluq.lava.common.natives;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract instance of a class which holds native resources that must be freed.
 */
public abstract class NativeResourceHolder implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(NativeResourceHolder.class);

  private final AtomicBoolean released = new AtomicBoolean();

  /**
   * Assert that the native resources have not been freed.
   */
  protected void checkNotReleased() {
    if (released.get()) {
      throw new IllegalStateException("Cannot use the decoder after closing it.");
    }
  }

  /**
   * Free up native resources of the decoder. Using other methods after this will throw IllegalStateException.
   */
  public void close() {
    closeInternal();
  }

  /**
   * Free the native resources.
   */
  protected abstract void freeResources();

  private synchronized void closeInternal() {
    if (released.compareAndSet(false, true)) {
      freeResources();
    }
  }
}
