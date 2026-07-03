package com.sedmelluq.lavaplayer.extensions.cache;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public class CachingAudioPlayerManager extends DefaultAudioPlayerManager implements AudioPlayerManager {
    private static final Logger log = LoggerFactory.getLogger(CachingAudioPlayerManager.class);

    private volatile AudioLoadCache audioLoadCache;

    public void setAudioLoadCache(AudioLoadCache audioLoadCache) {
        this.audioLoadCache = audioLoadCache;
    }

    @Override
    public void shutdown() {
        if (audioLoadCache != null) {
            audioLoadCache.close();
        }

        super.shutdown();
    }

    private Callable<Void> createItemLoader(final AudioReference reference, final AudioLoadResultHandler resultHandler) {
        return () -> {
            boolean[] reported = new boolean[1];

            try {
                AudioLoadResultHandler effectiveHandler = resultHandler;

                AudioLoadCache cache = audioLoadCache;

                if (cache != null) {
                    try {
                        CachedLoadResult cachedResult = cache.get(this, reference);

                        if (cachedResult != null) {
                            boolean dispatched = cachedResult.dispatch(this, resultHandler);

                            if (dispatched) {
                                log.debug("Loaded item from Redis cache with identifier {}.", reference.identifier);
                                return null;
                            }

                            cache.invalidate(reference);
                        }
                    } catch (Throwable cacheThrowable) {
                        log.debug("Redis cache lookup failed for {}. Falling back to source managers.",
                                reference.identifier, cacheThrowable);
                    }

                    effectiveHandler = new CachingAudioLoadResultHandler(
                            this,
                            reference,
                            resultHandler,
                            cache
                    );
                }

                if (!checkSourcesForItem(reference, effectiveHandler, reported)) {
                    log.debug("No matches for track with identifier {}.", reference.identifier);

                    if (cache != null) {
                        try {
                            cache.putNoMatches(reference);
                        } catch (Throwable cacheThrowable) {
                            log.debug("Failed to cache no-matches result for {}.", reference.identifier, cacheThrowable);
                        }
                    }

                    resultHandler.noMatches();
                }
            } catch (Throwable throwable) {
                if (reported[0]) {
                    log.warn("Load result handler for {} threw an exception", reference.identifier, throwable);
                } else {
                    dispatchItemLoadFailure(reference.identifier, resultHandler, throwable);
                }

                ExceptionTools.rethrowErrors(throwable);
            }

            return null;
        };
    }
}
