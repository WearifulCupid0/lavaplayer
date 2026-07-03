package com.sedmelluq.lavaplayer.extensions.cache.policy;

import java.time.Duration;

public class CachePolicyBuilder {
    private Duration trackTtl;
    private Duration searchTtl;
    private Duration playlistTtl;
    private Duration noMatchesTtl;

    public Duration getTrackTtl() {
        return trackTtl;
    }

    public Duration getSearchTtl() {
        return searchTtl;
    }

    public Duration getPlaylistTtl() {
        return playlistTtl;
    }

    public Duration getNoMatchesTtl() {
        return noMatchesTtl;
    }

    public CachePolicyBuilder setTrackTtl(Duration duration) {
        trackTtl = duration;
        return this;
    }

    public CachePolicyBuilder setSearchTtl(Duration duration) {
        searchTtl = duration;
        return this;
    }

    public CachePolicyBuilder setPlaylistTtl(Duration duration) {
        playlistTtl = duration;
        return this;
    }

    public CachePolicyBuilder setNoMatchesTtl(Duration duration) {
        noMatchesTtl = duration;
        return this;
    }

    public CachePolicy build() {
        if (trackTtl == null)
            throw new NullPointerException("CachePolicyBuilder trackTtl can't be a null value.");
        if (searchTtl == null)
            throw new NullPointerException("CachePolicyBuilder searchTtl can't be a null value.");
        if (playlistTtl == null)
            throw new NullPointerException("CachePolicyBuilder playlistTtl can't be a null value.");
        if (noMatchesTtl == null)
            throw new NullPointerException("CachePolicyBuilder noMatchesTtl can't be a null value.");

        return new CachePolicy(trackTtl, searchTtl, playlistTtl, noMatchesTtl);
    }
}
