package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.function.Function;

public class MixcloudUtils {
    public static AudioTrack extractTrack(JsonBrowser trackInfo, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return trackFactory.apply(new AudioTrackInfo(
            trackInfo.get("name").text(),
            trackInfo.get("owner").get("displayName").text(),
            (long) (trackInfo.get("audioLength").as(Double.class) * 1000.0),
            trackInfo.get("url").text(),
            false,
            trackInfo.get("url").text(),
            trackInfo.get("picture").get("url").text()
        ));
    }
}