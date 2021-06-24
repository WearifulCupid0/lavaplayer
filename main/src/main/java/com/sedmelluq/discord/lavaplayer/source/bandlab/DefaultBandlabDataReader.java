package com.sedmelluq.discord.lavaplayer.source.bandlab;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import static com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabConstants.TRACK_URI;

public class DefaultBandlabDataReader implements BandlabDataReader {
    public AudioTrackInfo readTrackInfo(JsonBrowser trackData, Boolean fromAlbum) {
        if(fromAlbum) {
            return new AudioTrackInfo(
                trackData.get("track").get("name").safeText(),
                trackData.get("creator").get("name").safeText(),
                (long) (trackData.get("track").get("sample").get("duration").as(Double.class) * 1000.0),
                DataFormatTools.extractBetween(trackData.get("track").get("sample").get("waveformUrl").text(), "/revisions-formatted/", "/"),
                false,
                String.format(TRACK_URI, trackData.get("creator").get("username").text(), trackData.get("albumId").text(), trackData.get("id").text()),
                getArtwork(trackData.get("track").get("picture"))
            );
        } else {
            JsonBrowser authorData = trackData.get("author").isNull() ? trackData.get("creator") : trackData.get("author");
            return new AudioTrackInfo(
                trackData.get("name").safeText(),
                authorData.get("name").safeText(),
                (long) (trackData.get("revision").get("mixdown").get("duration").as(Double.class) * 1000.0),
                trackData.get("revision").get("mixdown").get("id").safeText(),
                false,
                getUri(authorData.get("username").text(), trackData.get("slug").isNull() ? trackData.get("revision").get("song").get("slug").text() : trackData.get("slug").text()),
                getArtwork(trackData.get("picture"))
            );
        }
    }
    public String getArtwork(JsonBrowser picture) {
        return picture.get("url").text() + "1024x1024";
    }
    public String getUri(String username, String slug) {
        return "https://www.bandlab.com/" + username + "/" + slug;
    }
}