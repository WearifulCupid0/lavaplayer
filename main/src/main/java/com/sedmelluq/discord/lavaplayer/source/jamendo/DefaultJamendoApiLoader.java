package com.sedmelluq.discord.lavaplayer.source.jamendo;

import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.PBJUtils;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;
import static com.sedmelluq.discord.lavaplayer.source.jamendo.JamendoConstants.*;

public class DefaultJamendoApiLoader implements JamendoApiLoader {
    private final JamendoAudioSourceManager sourceManager;

    public DefaultJamendoApiLoader(JamendoAudioSourceManager sourceManager) {
        this.sourceManager = sourceManager;
    }

    @Override
    public AudioTrack loadTrack(String id) {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            URI uri = URI.create(TRACK_API_URL + "?id=" + id);
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "track response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                if(json.get("results").isNull() || json.get("results").index(0).isNull()) {
                    return null;
                }
                JsonBrowser info = json.get("results").index(0);

                return buildTrack(info, info.get("artist_name").text());
            }
        } catch(Exception e) {
            throw ExceptionTools.wrapUnfriendlyExceptions("Failed to load Jamendo track data.", SUSPICIOUS, e);
        }
    }

    @Override
    public AudioPlaylist loadAlbum(String id) {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            URI uri = URI.create(ALBUM_API_URL + "?id=" + id);
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "album response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                if(json.get("results").isNull() || json.get("results").index(0).isNull()) {
                    return null;
                }
                JsonBrowser info = json.get("results").index(0);

                List<AudioTrack> tracks = new ArrayList<>();
                String author = info.get("artist_name").text();
                String artwork = PBJUtils.getJamendoThumbnail(info);
                info.get("tracks").values()
                .forEach(track -> tracks.add(buildTrack(track, author, artwork)));

                return new BasicAudioPlaylist(
                    info.get("name").text(), author, artwork,
                    ALBUM_URL + info.get("id").text(),
                    "album", tracks, null, false
                );
            }
        } catch(Exception e) {
            throw ExceptionTools.wrapUnfriendlyExceptions("Failed to load Jamendo album data.", SUSPICIOUS, e);
        }
    }

    @Override
    public AudioPlaylist loadArtist(String id) {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            URI uri = URI.create(ARTIST_API_URL + "?id=" + id);
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "artist response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                if(json.get("results").isNull() || json.get("results").index(0).isNull()) {
                    return null;
                }
                JsonBrowser info = json.get("results").index(0);

                List<AudioTrack> tracks = new ArrayList<>();
                String author = info.get("name").text();
                info.get("tracks").values()
                .forEach(track -> tracks.add(buildTrack(track, author)));

                return new BasicAudioPlaylist(
                    author, author,
                    info.get("image").text().replace("1.200", "1.500"),
                    ARTIST_URL + info.get("id").text(),
                    "artist", tracks, null, false
                );
            }
        } catch(Exception e) {
            throw ExceptionTools.wrapUnfriendlyExceptions("Failed to load Jamendo artist data.", SUSPICIOUS, e);
        }
    }

    @Override
    public AudioPlaylist loadPlaylist(String id) {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            URI uri = URI.create(PLAYLIST_API_URL + "?id=" + id);
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "playlist response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                if(json.get("results").isNull() || json.get("results").index(0).isNull()) {
                    return null;
                }
                JsonBrowser info = json.get("results").index(0);

                List<AudioTrack> tracks = new ArrayList<>();
                info.get("tracks").values()
                .forEach(track -> tracks.add(buildTrack(track, track.get("artist_name").text())));

                return new BasicAudioPlaylist(
                    info.get("name").text(),
                    info.get("user_name").text(),
                    null, PLAYLIST_URL + info.get("id").text(),
                    "playlist", tracks, null, false
                );
            }
        } catch (Exception e) {
            throw ExceptionTools.wrapUnfriendlyExceptions("Failed to load Jamendo playlist data.", SUSPICIOUS, e);
        }
    }

    @Override
    public AudioPlaylist loadSearchResults(String query) {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            URI uri = URI.create(TRACK_API_URL + "?search=" + query);
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "search result response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                if(json.get("results").isNull() || json.get("results").index(0).isNull()) {
                    return null;
                }

                List<AudioTrack> tracks = new ArrayList<>();
                json.get("results").values()
                .forEach(track -> tracks.add(buildTrack(track, track.get("artist_name").text())));

                return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
            }
        } catch(Exception e) {
            throw ExceptionTools.wrapUnfriendlyExceptions("Failed to load Jamendo search results data.", SUSPICIOUS, e);
        }
    }

    private AudioTrack buildTrack(JsonBrowser track, String author) {
        return buildTrack(track, author, null);
    }

    private AudioTrack buildTrack(JsonBrowser track, String author, String albumImage) {
        String identifier = track.get("id").safeText();

        AudioTrackInfo info = new AudioTrackInfo(
            track.get("name").safeText(),
            author,
            (long) (track.get("duration").as(Double.class) * 1000.0),
            identifier,
            false,
            TRACK_URL + identifier,
            albumImage != null ? albumImage : PBJUtils.getJamendoThumbnail(track)
        );

        return new JamendoAudioTrack(info, sourceManager);
    };
}