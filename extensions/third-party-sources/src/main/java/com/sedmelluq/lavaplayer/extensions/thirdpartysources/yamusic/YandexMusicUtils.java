package com.sedmelluq.lavaplayer.extensions.thirdpartysources.yamusic;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.PBJUtils;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.function.Function;
import java.util.stream.Collectors;

public class YandexMusicUtils {
  private static final String TRACK_URL_FORMAT = "https://music.yandex.ru/album/%s/track/%s";

  public static AudioTrack extractTrack(JsonBrowser trackInfo, Function<AudioTrackInfo, AudioTrack> trackFactory) {
    if (!trackInfo.get("track").isNull()) {
      trackInfo = trackInfo.get("track");
    }
    String artists = trackInfo.get("artists").values().stream()
        .map(e -> e.get("name").text())
        .collect(Collectors.joining(", "));

    String trackId = trackInfo.get("id").text();

    JsonBrowser album = trackInfo.get("albums").index(0);

    String albumId = album.get("id").text();

    return trackFactory.apply(new AudioTrackInfo(
        trackInfo.get("title").text(),
        artists,
        trackInfo.get("durationMs").as(Long.class),
        trackInfo.get("id").text(),
        false,
        String.format(TRACK_URL_FORMAT, albumId, trackId),
        PBJUtils.getYandexMusicArtwork(trackInfo)
    ));
  }
}
