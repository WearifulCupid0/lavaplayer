package com.sedmelluq.discord.lavaplayer.source.bandlab;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.PBJUtils;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import static com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabConstants.SINGLE_TRACK_URI;
import static com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabConstants.TRACK_URI;

public class BandlabUtils {

    public static AudioTrackInfo buildTrackInfo(JsonBrowser trackData) {
        JsonBrowser track = trackData.get("track").isNull() ? trackData : trackData.get("track");
        JsonBrowser authorData = trackData.get("author").isNull() ? trackData.get("creator") : trackData.get("author");
        JsonBrowser mixdown = trackData.get("revision").get("mixdown").isNull() ? track.get("sample") : trackData.get("revision").get("mixdown");
        return new AudioTrackInfo(
            track.get("name").safeText(),
            authorData.get("name").safeText(),
            (long) (mixdown.get("duration").as(Double.class) * 1000.0),
            mixdown.get("waveformUrl").isNull() ? mixdown.get("id").text() : DataFormatTools.extractBetween(mixdown.get("waveformUrl").text(), "/revisions-formatted/", "/"),
            false,
            trackData.get("albumId").isNull()
            ? String.format(SINGLE_TRACK_URI, authorData.get("slug").text(), track.get("slug").text())
            : String.format(TRACK_URI, authorData.get("username").text(), trackData.get("albumId").text(), track.get("id").text()),
            PBJUtils.getBandlabPicture(trackData)
        );
    }
}
