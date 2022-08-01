package com.sedmelluq.discord.lavaplayer.remote.message;

/**
 * Node will send track explicit when the song is explicit and only non-explicit songs can be played
 */
public class TrackExplicitMessage implements RemoteMessage {
    /**
     * The ID for the track executor
     */
    public final long executorId;

    /**
     * @param executorId The ID for the track executor
     */
    public TrackExplicitMessage(long executorId) {
        this.executorId = executorId;
    }
}
