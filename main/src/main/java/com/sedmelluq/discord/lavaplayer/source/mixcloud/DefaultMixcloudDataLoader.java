package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import java.util.ArrayList;
import java.util.function.Function;

import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.PLAYLIST_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.ARTIST_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.TRACK_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudUtils.extractTrack;

public class DefaultMixcloudDataLoader extends AbstractMixcloudApiLoader implements MixcloudDataLoader {
    public AudioItem getTrack(String slug, String username, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi(String.format(TRACK_PAYLOAD, username, slug), (httpClient, data) -> {
            return extractTrack(data.get("cloudcast"), trackFactory);
        });
    }

    public AudioItem getArtist(String username, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi(String.format(ARTIST_PAYLOAD, username), (httpClient, data) -> {
            ArrayList<AudioTrack> tracks = new ArrayList<>();
            data.get("user").get("uploads").get("edges").values()
            .forEach(d -> {
                tracks.add(extractTrack(d.get("node"), trackFactory));
            });

            return new BasicAudioPlaylist(data.get("user").get("displayName").text(), "artist", tracks, null, false);
        });
    }

    public AudioItem getPlaylist(String slug, String username, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi(String.format(PLAYLIST_PAYLOAD, username, slug), (httpClient, data) -> {
            ArrayList<AudioTrack> tracks = new ArrayList<>();
            data.get("playlist").get("items").get("edges").values()
            .forEach(d -> {
                tracks.add(extractTrack(d.get("node").get("cloudcast"), trackFactory));
            });

            return new BasicAudioPlaylist(data.get("playlist").get("name").text(), "playlist", tracks, null, false);
        });
    }
}
