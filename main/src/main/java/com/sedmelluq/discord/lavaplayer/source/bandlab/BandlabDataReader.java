package com.sedmelluq.discord.lavaplayer.source.bandlab;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public interface BandlabDataReader {
    public AudioTrackInfo readTrackInfo(JsonBrowser trackData, Boolean fromAlbum);
    public String getArtwork(JsonBrowser picture);
    public String getUri(String username, String slug);
}