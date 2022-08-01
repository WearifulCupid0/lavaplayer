package com.sedmelluq.discord.lavaplayer.remote.message;

/**
 * Node will send track stuck when the song takes too long to start
 */
public class TrackStuckMessage implements RemoteMessage {
    /**
     * The ID for the track executor
     */
    public final long executorId;
    /**
     * Exception that was thrown by the local executor
     */
    public final long thresholdMs;

    /**
     * @param executorId The ID for the track executor
     * @param thresholdMs Max thread shot
     */
    public TrackStuckMessage(long executorId, long thresholdMs) {
        this.executorId = executorId;
        this.thresholdMs = thresholdMs;
    }
}
