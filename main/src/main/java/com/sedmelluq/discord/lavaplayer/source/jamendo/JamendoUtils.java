package com.sedmelluq.discord.lavaplayer.source.jamendo;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.function.Function;

public class JamendoUtils {
  private static final String TRACK_URL_FORMAT = "https://www.jamendo.com/track/%s";

  public static AudioTrack extractTrack(JsonBrowser trackInfo, String artist, String artwork, Function<AudioTrackInfo, AudioTrack> trackFactory) {
    String id = trackInfo.get("id").text();

    return trackFactory.apply(new AudioTrackInfo(
        trackInfo.get("name").text(),
        artist,
        (long) (trackInfo.get("duration").as(Double.class) * 1000.0),
        id,
        false,
        String.format(TRACK_URL_FORMAT, id),
        artwork
    ));
  }
}
