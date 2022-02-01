package com.sedmelluq.discord.lavaplayer.source.iheart;

import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.function.Function;

public interface iHeartApiHandler {
    ExtendedHttpConfigurable getHttpConfiguration();

    AudioTrack radio(String id, Function<AudioTrackInfo, AudioTrack> trackFactory);
    AudioPlaylist search(String query, Function<AudioTrackInfo, AudioTrack> trackFactory);
    AudioPlaylist podcast(String id, String episodeId, Function<AudioTrackInfo, AudioTrack> trackFactory);
}