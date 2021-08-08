package com.sedmelluq.discord.lavaplayer.source.saavn;

import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import java.util.function.Function;

public interface SaavnSearchProvider {
    public ExtendedHttpConfigurable getHttpConfiguration();
    public AudioItem loadSearchResults(String query, Function<AudioTrackInfo, AudioTrack> trackFactory);
}
