package com.sedmelluq.discord.lavaplayer.track;

import com.sedmelluq.discord.lavaplayer.tools.ExplicitContentException;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

/**
 * Audio track which delegates its processing to another track. The delegate does not have to be known when the
 * track is created, but is passed when processDelegate() is called.
 */
public abstract class DelegatedAudioTrack extends BaseAudioTrack {
  private InternalAudioTrack delegate;

  /**
   * @param trackInfo Track info
   */
  public DelegatedAudioTrack(AudioTrackInfo trackInfo) {
    super(trackInfo);
  }

  protected synchronized void processDelegate(InternalAudioTrack delegate, LocalAudioTrackExecutor localExecutor)
      throws Exception {

    this.delegate = delegate;
    if (!localExecutor.isAllowedExplicit() && delegate.getInfo().explicit) throw new ExplicitContentException(this);


    delegate.assignExecutor(localExecutor, false);
    delegate.process(localExecutor);
  }

  @Override
  public long getDuration() {
    if (delegate != null) {
      return delegate.getDuration();
    } else {
      synchronized (this) {
        if (delegate != null) {
          return delegate.getDuration();
        } else {
          return super.getDuration();
        }
      }
    }
  }
}
