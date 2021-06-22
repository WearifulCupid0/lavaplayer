package com.sedmelluq.discord.lavaplayer.source.jamendo;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.function.Function;

public interface JamendoPlaylistLoader extends JamendoApiLoader {
    AudioItem loadPlaylist(String id, String type, String clientId, Function<AudioTrackInfo, AudioTrack> trackFactory);
}