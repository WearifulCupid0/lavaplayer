package com.sedmelluq.discord.lavaplayer.tools;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public class ExplicitContentException extends Exception {
    public final AudioTrack audioTrack;

    public ExplicitContentException(AudioTrack audioTrack) {
        super();

        this.audioTrack = audioTrack;
    }
}
