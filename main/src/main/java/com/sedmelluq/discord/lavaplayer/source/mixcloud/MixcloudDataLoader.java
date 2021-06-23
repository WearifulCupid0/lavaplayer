package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import java.util.function.Function;

public interface MixcloudDataLoader extends MixcloudApiLoader {
    public AudioItem getTrack(String slug, String username, Function<AudioTrackInfo, AudioTrack> trackFactory);
    public AudioItem getArtist(String username, Function<AudioTrackInfo, AudioTrack> trackFactory);
    public AudioItem getPlaylist(String slug, String username, Function<AudioTrackInfo, AudioTrack> trackFactory);
    public AudioItem getSearchResults(String query, Function<AudioTrackInfo, AudioTrack> trackFactory);
}
