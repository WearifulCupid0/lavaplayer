package com.sedmelluq.discord.lavaplayer.source.jamendo;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.function.Function;

public class DefaultJamendoTrackLoader extends AbstractJamendoApiLoader implements JamendoTrackLoader {
    private static final String TRACKS_INFO_FORMAT = "https://api.jamendo.com/v3.0/tracks?id=";

    @Override
    public AudioItem loadTrack(String trackId, String clientId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi(TRACKS_INFO_FORMAT + trackId, clientId, (httpClient, results) -> {
            JsonBrowser trackInfo = results.index(0);
            if (trackInfo.isNull()) return AudioReference.NO_TRACK;
            return JamendoUtils.extractTrack(
                trackInfo,
                trackInfo.get("artist_name").text(),
                trackInfo.get("image").text().replace("width=200", "width=500"),
                trackFactory
            );
        });
    }
}
