package com.sedmelluq.discord.lavaplayer.track;

import org.jetbrains.annotations.Nullable;

public class AudioTrackAuthorInfo {
    /**
     * Track author name, if known
     */
    public final String name;
    /**
     * Track author url, if known
     */
    @Nullable
    public final String uri;

    public AudioTrackAuthorInfo(String name) {
        this(name, null);
    }

    public AudioTrackAuthorInfo(String name, String uri) {
        this.name = name;
        this.uri = uri;
    }
}
