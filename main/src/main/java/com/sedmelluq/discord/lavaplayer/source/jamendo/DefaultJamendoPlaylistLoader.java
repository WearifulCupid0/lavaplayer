package com.sedmelluq.discord.lavaplayer.source.jamendo;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.*;

import java.util.ArrayList;
import java.util.function.Function;

public class DefaultJamendoPlaylistLoader extends AbstractJamendoApiLoader implements JamendoPlaylistLoader {
    private static final String PLAYLIST_INFO_FORMAT = "https://api.jamendo.com/v3.0/playlists/tracks?id=";
    private static final String ALBUM_INFO_FORMAT = "https://api.jamendo.com/v3.0/albums/tracks?id=";
    private static final String ARTIST_INFO_FORMAT = "https://api.jamendo.com/v3.0/artists/tracks?id=";

    @Override
    public AudioItem loadPlaylist(String id, String type, String clientId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        if (type == "playlist") return loadPlaylist(id, clientId, trackFactory);
        if (type == "album") return loadAlbum(id, clientId, trackFactory);
        if (type == "artist") return loadArtist(id, clientId, trackFactory);

        return AudioReference.NO_TRACK;
    }

    private AudioItem loadPlaylist(String id, String clientId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi(PLAYLIST_INFO_FORMAT + id, clientId, (httpClient, results) -> {
            JsonBrowser playlist = results.index(0);
            if (playlist.isNull()) return AudioReference.NO_TRACK;
            ArrayList<AudioTrack> tracks = new ArrayList<>();
            playlist.get("tracks").values()
            .forEach(track -> {
                String artist = track.get("artist_name").text();
                String artwork = track.get("image").text().replace("width=200", "width=500");
                tracks.add(JamendoUtils.extractTrack(track, artist, artwork, trackFactory));
            });
            return new BasicAudioPlaylist(
                playlist.get("name").text(),
                playlist.get("user_name").text(),
                null,
                "https://www.jamendo.com/playlist/" + id,
                "playlist",
                tracks,
                null,
                false
            );
        });
    }

    private AudioItem loadAlbum(String id, String clientId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi(ALBUM_INFO_FORMAT + id, clientId, (httpClient, results) -> {
            JsonBrowser album = results.index(0);
            if (album.isNull()) return AudioReference.NO_TRACK;
            String artist = album.get("artist_name").text();
            String artwork = album.get("image").text().replace("1.200", "1.500");
            ArrayList<AudioTrack> tracks = new ArrayList<>();
            album.get("tracks").values()
            .forEach(track -> {
                tracks.add(JamendoUtils.extractTrack(track, artist, artwork, trackFactory));
            });
            return new BasicAudioPlaylist(
                album.get("name").text(),
                artist,
                artwork,
                "https://www.jamendo.com/album/" + id,
                "album",
                tracks,
                null,
                false
            );
        });
    }

    private AudioItem loadArtist(String id, String clientId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        return extractFromApi(ARTIST_INFO_FORMAT + id, clientId, (httpClient, results) -> {
            JsonBrowser artist = results.index(0);
            if (artist.isNull()) return AudioReference.NO_TRACK;
            String artistName = artist.get("name").text();
            ArrayList<AudioTrack> tracks = new ArrayList<>();
            artist.get("tracks").values()
            .forEach(track -> {
                String artwork = track.get("image").text().replace("width=200", "width=500");
                tracks.add(JamendoUtils.extractTrack(track, artistName, artwork, trackFactory));
            });
            return new BasicAudioPlaylist(
                artistName,
                artistName,
                artist.get("image").text().replace("1.200", "1.500"),
                "https://www.jamendo.com/artist/" + id,
                "artist",
                tracks,
                null,
                false
            );
        });
    }
}
