package com.sedmelluq.discord.lavaplayer.source.jamendo;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public interface JamendoApiLoader {
    AudioTrack loadTrack(String id);
    AudioPlaylist loadAlbum(String id);
    AudioPlaylist loadArtist(String id);
    AudioPlaylist loadPlaylist(String id);
    AudioPlaylist loadSearchResults(String query);
}