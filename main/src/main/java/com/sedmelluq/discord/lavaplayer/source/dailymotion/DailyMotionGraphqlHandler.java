package com.sedmelluq.discord.lavaplayer.source.dailymotion;

import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.function.Function;

public interface DailyMotionGraphqlHandler {
    ExtendedHttpConfigurable getHttpConfiguration();

    AudioTrack video(String id, Function<AudioTrackInfo, AudioTrack> trackFactory);
    AudioPlaylist similar(String id, Function<AudioTrackInfo, AudioTrack> trackFactory);
    AudioPlaylist playlist(String id, Function<AudioTrackInfo, AudioTrack> trackFactory);
    AudioPlaylist channel(String slug, Function<AudioTrackInfo, AudioTrack> trackFactory);
    AudioPlaylist search(String query, Function<AudioTrackInfo, AudioTrack> trackFactory);
}