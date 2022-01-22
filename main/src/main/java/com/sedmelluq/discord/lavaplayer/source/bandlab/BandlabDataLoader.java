package com.sedmelluq.discord.lavaplayer.source.bandlab;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;

import java.util.function.Function;

public interface BandlabDataLoader extends BandlabApiLoader {
    public AudioTrack loadTrack(String username, String slug, Function<AudioTrackInfo, AudioTrack> trackFactory);
    public AudioPlaylist loadCollection(String collectionId, Function<AudioTrackInfo, AudioTrack> trackFactory);
    public AudioPlaylist loadAlbum(String albumId, Function<AudioTrackInfo, AudioTrack> trackFactory);
}