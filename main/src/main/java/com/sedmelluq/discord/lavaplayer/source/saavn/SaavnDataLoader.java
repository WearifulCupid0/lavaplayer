package com.sedmelluq.discord.lavaplayer.source.saavn;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.function.Function;

public interface SaavnDataLoader extends SaavnApiLoader {
    AudioPlaylist loadSearch(String query, Function<AudioTrackInfo, AudioTrack> trackFactory);
    AudioTrack loadTrack(String identifier, Function<AudioTrackInfo, AudioTrack> trackFactory);
    AudioPlaylist loadAlbum(String identifier, Function<AudioTrackInfo, AudioTrack> trackFactory);
    AudioPlaylist loadPlaylist(String identifier, Function<AudioTrackInfo, AudioTrack> trackFactory);
}
