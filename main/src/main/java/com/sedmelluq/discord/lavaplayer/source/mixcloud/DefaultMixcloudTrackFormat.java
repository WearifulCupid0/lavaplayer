package com.sedmelluq.discord.lavaplayer.source.mixcloud;

public class DefaultMixcloudTrackFormat implements MixcloudTrackFormat {
    private final String protocol;
    private final String playback;

    public DefaultMixcloudTrackFormat(String protocol, String playback) {
        this.protocol = protocol;
        this.playback = playback;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public String getPlaybackUrl() {
        return playback;
    }
}