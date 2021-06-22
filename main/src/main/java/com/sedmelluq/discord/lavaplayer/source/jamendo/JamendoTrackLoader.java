package com.sedmelluq.discord.lavaplayer.source.jamendo;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.function.Function;

public interface JamendoTrackLoader extends JamendoApiLoader {
  AudioItem loadTrack(String trackId, String clientId, Function<AudioTrackInfo, AudioTrack> trackFactory);
}
