package com.sedmelluq.lavaplayer.extensions.cache;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachingAudioLoadResultHandler implements AudioLoadResultHandler {
    private static final Logger log = LoggerFactory.getLogger(CachingAudioLoadResultHandler.class);

    private final AudioPlayerManager manager;
    private final AudioReference reference;
    private final AudioLoadResultHandler delegate;
    private final AudioLoadCache cache;

    public CachingAudioLoadResultHandler(
            AudioPlayerManager manager,
            AudioReference reference,
            AudioLoadResultHandler delegate,
            AudioLoadCache cache
    ) {
        this.manager = manager;
        this.reference = reference;
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public void trackLoaded(AudioTrack track) {
        try {
            cache.putTrack(manager, reference, track);
        } catch (Exception e) {
            log.debug("Failed to cache loaded track {}.", reference.identifier, e);
        }

        delegate.trackLoaded(track);
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        try {
            cache.putPlaylist(manager, reference, playlist);
        } catch (Exception e) {
            log.debug("Failed to cache loaded playlist {}.", reference.identifier, e);
        }

        delegate.playlistLoaded(playlist);
    }

    @Override
    public void noMatches() {
        try {
            cache.putNoMatches(reference);
        } catch (Exception e) {
            log.debug("Failed to cache no-matches result {}.", reference.identifier, e);
        }

        delegate.noMatches();
    }

    @Override
    public void loadFailed(FriendlyException exception) {
        delegate.loadFailed(exception);
    }
}