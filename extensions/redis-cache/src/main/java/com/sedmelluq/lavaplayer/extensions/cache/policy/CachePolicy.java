package com.sedmelluq.lavaplayer.extensions.cache.policy;

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.lavaplayer.extensions.cache.CacheableAudioSourceManager;

import java.time.Duration;

public class CachePolicy {
    private final Duration trackTtl;
    private final Duration searchTtl;
    private final Duration playlistTtl;
    private final Duration noMatchesTtl;

    public CachePolicy(
            Duration trackTtl,
            Duration searchTtl,
            Duration playlistTtl,
            Duration noMatchesTtl
    ) {
        this.trackTtl = trackTtl;
        this.searchTtl = searchTtl;
        this.playlistTtl = playlistTtl;
        this.noMatchesTtl = noMatchesTtl;
    }

    public static CachePolicy defaultPolicy() {
        return new CachePolicy(
                Duration.ofHours(12),
                Duration.ofMinutes(30),
                Duration.ofMinutes(30),
                Duration.ofMinutes(2)
        );
    }

    public boolean shouldCacheTrack(AudioReference reference, AudioTrack track) {
        if (track == null || track.getInfo() == null) {
            return false;
        }

        AudioSourceManager sourceManager = track.getSourceManager();

        if (sourceManager == null) {
            return false;
        }

        if (sourceManager instanceof CacheableAudioSourceManager) {
            return ((CacheableAudioSourceManager) sourceManager).shouldCacheTrack(track);
        }

        String sourceName = sourceManager.getSourceName();

        return !sourceName.equals("http") && !sourceName.equals("local");
    }

    public boolean shouldCachePlaylist(AudioReference reference, AudioPlaylist playlist) {
        if (playlist == null || playlist.getTracks() == null || playlist.getTracks().isEmpty()) {
            return false;
        }

        return playlist.getTracks().size() <= 100;
    }

    public boolean shouldCacheNoMatches(AudioReference reference) {
        return reference.identifier != null;
    }

    public Duration trackTtl(AudioTrack track) {
        AudioSourceManager sourceManager = track.getSourceManager();
        if (sourceManager instanceof CacheableAudioSourceManager) {
            return ((CacheableAudioSourceManager) sourceManager).getTrackTtl(track);
        }

        return trackTtl;
    }

    public Duration playlistTtl(AudioPlaylist playlist) {
        return playlist.isSearchResult() ? searchTtl : playlistTtl;
    }

    public Duration noMatchesTtl() {
        return noMatchesTtl;
    }
}