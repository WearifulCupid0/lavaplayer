package com.sedmelluq.discord.lavaplayer.player.event;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Event that is fired when an audio track ends in an audio player, either by interruption, exception or reaching the end.
 */
public class TrackExplicitEvent extends AudioEvent {
    /**
     * Audio track that ended
     */
    public final AudioTrack track;
    /**
     * @param player Audio player
     * @param track Audio track that ended
     */
    public TrackExplicitEvent(AudioPlayer player, AudioTrack track) {
        super(player);
        this.track = track;
    }
}
