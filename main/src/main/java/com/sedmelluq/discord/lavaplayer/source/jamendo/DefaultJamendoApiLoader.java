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

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class DefaultJamendoApiLoader implements JamendoApiLoader {
    private final JamendoAudioSourceManager sourceManager;

    public DefaultJamendoApiLoader(JamendoAudioSourceManager sourceManager) {
        this.sourceManager = sourceManager;
    }

    @Override
    public AudioTrack loadTrack(String id) {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            URI uri = URI.create("https://api.jamendo.com/v3.0/tracks?id=" + id);
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "track response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                if(json.get("results").isNull() || json.get("results").index(0).isNull()) {
                    return null;
                }
                JsonBrowser info = json.get("results").index(0);

                AudioTrackInfo trackInfo = new AudioTrackInfo(
                    info.get("name").text(),
                    info.get("artist_name").text(),
                    (long) (info.get("duration").as(Double.class) * 1000.0),
                    info.get("id").text(),
                    false,
                    info.get("shareurl").text(),
                    PBJUtils.getJamendoThumbnail(info)
                );
                return new JamendoAudioTrack(trackInfo, sourceManager);
            }
        } catch(Exception e) {
            throw ExceptionTools.wrapUnfriendlyExceptions("Failed to load Jamendo track data.", SUSPICIOUS, e);
        }
    }

    @Override
    public AudioPlaylist loadAlbum(String id) {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            URI uri = URI.create("https://api.jamendo.com/v3.0/albums/tracks?id=" + id);
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
                .forEach(track -> {
                    String identifier = track.get("id").text();
                    AudioTrackInfo trackInfo = new AudioTrackInfo(
                        track.get("name").text(),
                        author,
                        (long) (track.get("duration").as(Double.class) * 1000.0),
                        identifier,
                        false,
                        "https://www.jamendo.com/track/" + identifier,
                        artwork
                    );
                    tracks.add(new JamendoAudioTrack(trackInfo, sourceManager));
                });

                return new BasicAudioPlaylist(
                    info.get("name").text(), author, artwork,
                    "https://www.jamendo.com/album/" + info.get("id").text(),
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
            URI uri = URI.create("https://api.jamendo.com/v3.0/artists/tracks?id=" + id);
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
                .forEach(track -> {
                    String identifier = track.get("id").text();
                    AudioTrackInfo trackInfo = new AudioTrackInfo(
                        track.get("name").text(),
                        author,
                        (long) (track.get("duration").as(Double.class) * 1000.0),
                        identifier,
                        false,
                        "https://www.jamendo.com/track/" + identifier,
                        PBJUtils.getJamendoThumbnail(track)
                    );
                    tracks.add(new JamendoAudioTrack(trackInfo, sourceManager));
                });

                return new BasicAudioPlaylist(
                    author, author,
                    info.get("image").text().replace("1.200", "1.500"),
                    "https://www.jamendo.com/artist/" + info.get("id").text(),
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
            URI uri = URI.create("https://api.jamendo.com/v3.0/playlists/tracks?id=" + id);
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "playlist response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                if(json.get("results").isNull() || json.get("results").index(0).isNull()) {
                    return null;
                }
                JsonBrowser info = json.get("results").index(0);

                List<AudioTrack> tracks = new ArrayList<>();
                info.get("tracks").values()
                .forEach(track -> {
                    String identifier = track.get("id").text();
                    AudioTrackInfo trackInfo = new AudioTrackInfo(
                        track.get("name").text(),
                        track.get("artist_name").text(),
                        (long) (track.get("duration").as(Double.class) * 1000.0),
                        identifier,
                        false,
                        "https://www.jamendo.com/track/" + identifier,
                        PBJUtils.getJamendoThumbnail(track)
                    );
                    tracks.add(new JamendoAudioTrack(trackInfo, sourceManager));
                });

                return new BasicAudioPlaylist(
                    json.get("name").text(),
                    json.get("user_name").text(),
                    null, "https://www.jamendo.com/playlist/" + json.get("id").text(),
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
            URI uri = URI.create("https://api.jamendo.com/v3.0/tracks?search=" + query);
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "search result response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                if(json.get("results").isNull() || json.get("results").index(0).isNull()) {
                    return null;
                }
                JsonBrowser info = json.get("results").index(0);

                List<AudioTrack> tracks = new ArrayList<>();
                info.values()
                .forEach(track -> {
                    AudioTrackInfo trackInfo = new AudioTrackInfo(
                        track.get("name").text(),
                        track.get("artist_name").text(),
                        (long) (track.get("duration").as(Double.class) * 1000.0),
                        track.get("id").text(),
                        false,
                        track.get("shareurl").text(),
                        PBJUtils.getJamendoThumbnail(track)
                    );
                    tracks.add(new JamendoAudioTrack(trackInfo, sourceManager));
                });

                return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
            }
        } catch(Exception e) {
            throw ExceptionTools.wrapUnfriendlyExceptions("Failed to load Jamendo search results data.", SUSPICIOUS, e);
        }
    }
}