package com.sedmelluq.discord.lavaplayer.source.saavn;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.function.Function;
import java.util.ArrayList;
import java.util.List;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public class DefaultSaavnDataLoader extends AbstractSaavnApiLoader implements SaavnDataLoader {
    public AudioPlaylist loadSearch(String query, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi("search.getResults", "p=1&n=30&q=" + query, (httpClient, response) -> {
            List<AudioTrack> tracks = new ArrayList<>();
            
            response.get("results").values()
            .forEach(result -> tracks.add(trackFactory.apply(buildInfo(result))));

            return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
        });
    }
    public AudioTrack loadTrack(String id, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi("webapi.get", "ctx=wap6dot0&type=song&token=" + id, (httpClient, response) -> {
            if(response.get("songs").isNull() || response.get("songs").index(0).isNull()) return null;
            JsonBrowser song = response.get("songs").index(0);
            return trackFactory.apply(buildInfo(song));
        });
    }
    public AudioPlaylist loadAlbum(String id, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi("webapi.get", "type=album&token=" + id, (httpClient, response) -> {
            List<AudioTrack> tracks = new ArrayList<>();

            response.get("songs").values()
            .forEach(song -> tracks.add(trackFactory.apply(buildInfo(song))));

            return new BasicAudioPlaylist(
                response.get("title").isNull() ? response.get("name").text() : response.get("title").text(),
                response.get("primary_artists").safeText(),
                response.get("image").text().replace("150x150", "500x500"),
                response.get("perma_url").text(),
                "album", tracks, null, false);
        });
    }
    public AudioPlaylist loadPlaylist(String id, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi("webapi.get", "type=playlist&p=1&n=1000&token=" + id, (httpClient, response) -> {
            List<AudioTrack> tracks = new ArrayList<>();

            response.get("songs").values()
            .forEach(song -> tracks.add(trackFactory.apply(buildInfo(song))));

            return new BasicAudioPlaylist(
                response.get("listname").text(),
                !response.get("lastname").isNull() && response.get("lastname").text().length() > 0
                ? response.get("firstname").text() + " " + response.get("lastname").text()
                : response.get("firstname").text(),
                response.get("image").text(),
                response.get("perma_url").text(),
                "playlist", tracks, null, false);
        });
    }

    private AudioTrackInfo buildInfo(JsonBrowser song) {
        String url = song.get("perma_url").text();
        String[] paths = url.split("/");
        return new AudioTrackInfo(
            song.get("song").safeText(),
            song.get("primary_artists").safeText(),
            (long) (song.get("duration").as(Double.class) * 1000.0),
            paths[paths.length - 1],
            false,
            url,
            song.get("image").text().replace("150x150", "500x500")
        );
    }
}
