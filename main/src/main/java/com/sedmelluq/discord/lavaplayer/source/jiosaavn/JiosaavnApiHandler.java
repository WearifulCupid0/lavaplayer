package com.sedmelluq.discord.lavaplayer.source.jiosaavn;

import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.function.Function;

public interface JiosaavnApiHandler {
    ExtendedHttpConfigurable getHttpConfiguration();
    
    AudioTrack track(String id, Function<AudioTrackInfo, AudioTrack> trackFactory);
    AudioPlaylist album(String id, Function<AudioTrackInfo, AudioTrack> trackFactory);
    AudioPlaylist playlist(String id, Function<AudioTrackInfo, AudioTrack> trackFactory);
    AudioPlaylist search(String query, Function<AudioTrackInfo, AudioTrack> trackFactory);
}
