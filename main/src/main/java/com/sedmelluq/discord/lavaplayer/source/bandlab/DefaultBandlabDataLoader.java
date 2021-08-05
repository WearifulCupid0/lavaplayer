package com.sedmelluq.discord.lavaplayer.source.bandlab;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;

import java.util.ArrayList;
import java.util.function.Function;

import static com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabConstants.COLLECTION_BANDLAB_API;
import static com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabConstants.SONG_POST_BANDLAB_API;
import static com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabConstants.ALBUM_BANDLAB_API;
import static com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabConstants.COLLECTION_URI;
import static com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabConstants.ALBUM_URI;

public class DefaultBandlabDataLoader extends AbstractBandlabApiLoader implements BandlabDataLoader {
    private BandlabDataReader dataReader;

    public void setDataReader(BandlabDataReader dataReader) {
        this.dataReader = dataReader;
    }

    public AudioTrack loadTrack(String username, String slug, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi(String.format(SONG_POST_BANDLAB_API, username, slug), (httpClient, response) -> {
            return trackFactory.apply(dataReader.readTrackInfo(response, false));
        });
    }

    public AudioPlaylist loadCollection(String collectionId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi(String.format(COLLECTION_BANDLAB_API, collectionId), (httpClient, response) -> {
            ArrayList<AudioTrack> tracks = new ArrayList<>();

            response.get("posts").values()
            .forEach(t -> {
                AudioTrackInfo info = dataReader.readTrackInfo(t, false);
                if(info != null) {
                    tracks.add(trackFactory.apply(info));
                }
            });

            if(tracks.isEmpty()) {
                return null;
            }

            return new BasicAudioPlaylist(
                response.get("name").text(),
                response.get("creator").get("name").text(),
                dataReader.getArtwork(response.get("picture")),
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
                AudioTrackInfo info = dataReader.readTrackInfo(t, true);
                if(info != null) {
                    tracks.add(trackFactory.apply(info));
                }
            });

            if(tracks.isEmpty()) {
                return null;
            }

            return new BasicAudioPlaylist(
                response.get("name").text(),
                response.get("creator").get("name").text(),
                dataReader.getArtwork(response.get("picture")),
                String.format(ALBUM_URI, response.get("creator").get("username").text(), albumId),
                "album",
                tracks,
                null,
                false
            );
        });
    }
}
