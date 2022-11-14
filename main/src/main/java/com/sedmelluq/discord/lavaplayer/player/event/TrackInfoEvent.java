package com.sedmelluq.discord.lavaplayer.player.event;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public class TrackInfoEvent extends AudioEvent {
    public final AudioTrack track;
    public TrackInfoEvent(AudioPlayer player, AudioTrack track) {
        super(player);
        this.track = track;
    }
}
