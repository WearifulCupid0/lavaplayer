package com.sedmelluq.discord.lavaplayer.player.event;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public class TrackExplicitEvent extends AudioEvent {
    public final AudioTrack track;
    public TrackExplicitEvent(AudioPlayer player, AudioTrack track) {
        super(player);
        this.track = track;
    }
}
