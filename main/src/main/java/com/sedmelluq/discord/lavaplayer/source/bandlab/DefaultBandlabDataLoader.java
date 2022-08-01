package com.sedmelluq.discord.lavaplayer.source.bandlab;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.PBJUtils;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;

import java.util.ArrayList;
import java.util.function.Function;

import static com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabConstants.COLLECTION_BANDLAB_API;
import static com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabConstants.SONG_POST_BANDLAB_API;
import static com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabConstants.ALBUM_BANDLAB_API;
import static com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabConstants.COLLECTION_URI;
import static com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabConstants.ALBUM_URI;

public class DefaultBandlabDataLoader extends AbstractBandlabApiLoader implements BandlabDataLoader {
    public AudioTrack loadTrack(String username, String slug, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi(String.format(SONG_POST_BANDLAB_API, username, slug), (httpClient, response) -> trackFactory.apply(BandlabUtils.buildTrackInfo(response)));
    }

    public AudioPlaylist loadCollection(String collectionId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi(String.format(COLLECTION_BANDLAB_API, collectionId), (httpClient, response) -> {
            ArrayList<AudioTrack> tracks = new ArrayList<>();

            response.get("posts").values()
            .forEach(t -> {
                AudioTrackInfo info = BandlabUtils.buildTrackInfo(t);
                tracks.add(trackFactory.apply(info));
            });

            if(tracks.isEmpty()) {
                return null;
            }

            return new BasicAudioPlaylist(
                response.get("name").text(),
                response.get("creator").get("name").text(),
                PBJUtils.getBandlabPicture(response),
                String.format(COLLECTION_URI, response.get("creator").get("username").text(), collectionId),
                "playlist",
                tracks,
                null,
                false
            );
        });
    }

    public AudioPlaylist loadAlbum(String albumId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi(String.format(ALBUM_BANDLAB_API, albumId), (httpClient, response) -> {
            ArrayList<AudioTrack> tracks = new ArrayList<>();

            response.get("posts").values()
            .forEach(t -> {
                AudioTrackInfo info = BandlabUtils.buildTrackInfo(t);
                tracks.add(trackFactory.apply(info));
            });

            if(tracks.isEmpty()) {
                return null;
            }

            return new BasicAudioPlaylist(
                response.get("name").text(),
                response.get("creator").get("name").text(),
                PBJUtils.getBandlabPicture(response),
                String.format(ALBUM_URI, response.get("creator").get("username").text(), albumId),
                "album",
                tracks,
                null,
                false
            );
        });
    }
}