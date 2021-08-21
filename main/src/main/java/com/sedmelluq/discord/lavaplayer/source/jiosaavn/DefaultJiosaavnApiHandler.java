package com.sedmelluq.discord.lavaplayer.source.jiosaavn;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class DefaultJiosaavnApiHandler implements JiosaavnApiHandler {
    private final HttpInterfaceManager httpInterfaceManager;

    public DefaultJiosaavnApiHandler() {
        this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
    }

    @Override
    public ExtendedHttpConfigurable getHttpConfiguration() {
        return httpInterfaceManager;
    }

    public AudioTrack track(String id, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI uri = new URIBuilder("https://www.jiosaavn.com/api.php")
            .addParameter("__call", "webapi.get")
            .addParameter("token", id)
            .addParameter("type", "song")
            .addParameter("ctx", "web6dot0")
            .addParameter("_format", "json")
            .addParameter("_marker", "0")
            .build();
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "track response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (!json.get("songs").isNull() && !json.get("songs").index(0).isNull()) {
                    JsonBrowser song = json.get("songs").index(0);
                    if (!song.get("encrypted_media_url").isNull() && song.get("id").text() != "dsf7m88e") {
                        return trackFactory.apply(buildInfo(song));
                    }
                }
                return null;
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load JioSaavn track", SUSPICIOUS, e);
        }
    }

    public AudioPlaylist album(String id, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI uri = new URIBuilder("https://www.jiosaavn.com/api.php")
            .addParameter("__call", "webapi.get")
            .addParameter("token", id)
            .addParameter("type", "album")
            .addParameter("ctx", "wap6dot0")
            .addParameter("_format", "json")
            .addParameter("_marker", "0")
            .build();
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "album response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (!json.get("songs").isNull() && !json.get("songs").index(0).isNull()) {
                    if(json.get("songs").index(0).get("id").text() != "dsf7m88e") {
                        List<AudioTrack> tracks = new ArrayList<>();
                        json.get("songs").values()
                        .forEach(song -> {
                            if (!song.get("encrypted_media_url").isNull()) tracks.add(trackFactory.apply(buildInfo(song)));
                        });
                        String name = json.get("title").text();
                        String creator = json.get("primary_artists").isList()
                        ? json.get("primary_artists").index(0).text()
                        : json.get("primary_artists").text();
                        String image = json.get("image").text().replace("150x150", "500x500");
                        String url = json.get("perma_url").text();
                        return new BasicAudioPlaylist(name, creator, image, url, "album", tracks, null, false);
                    }
                }
                return null;
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load JioSaavn album", SUSPICIOUS, e);
        }
    }

    public AudioPlaylist playlist(String id, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI uri = new URIBuilder("https://www.jiosaavn.com/api.php")
            .addParameter("__call", "webapi.get")
            .addParameter("token", id)
            .addParameter("type", "playlist")
            .addParameter("p", "1")
            .addParameter("n", "100")
            .addParameter("_format", "json")
            .addParameter("_marker", "0")
            .addParameter("ctx", "web6dot0")
            .build();
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "playlist response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (!json.get("songs").isNull() && !json.get("songs").index(0).isNull()) {
                    if(json.get("songs").index(0).get("id").text() != "dsf7m88e") {
                        List<AudioTrack> tracks = new ArrayList<>();
                        json.get("songs").values()
                        .forEach(song -> {
                            if (!song.get("encrypted_media_url").isNull()) tracks.add(trackFactory.apply(buildInfo(song)));
                        });
                        String name = json.get("listname").text();
                        String creator = !json.get("lastname").isNull() && json.get("lastname").text().length() > 0
                        ? json.get("firstname").text() + " " + json.get("lastname").text()
                        : json.get("firstname").text();
                        String image = json.get("image").text();
                        String url = json.get("perma_url").text();
                        return new BasicAudioPlaylist(name, creator, image, url, "playlist", tracks, null, false);
                    }
                }
                return null;
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load JioSaavn playlist", SUSPICIOUS, e);
        }
    }

    public AudioPlaylist search(String query, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI uri = new URIBuilder("https://www.jiosaavn.com/api.php")
            .addParameter("__call", "search.getResults")
            .addParameter("q", query)
            .addParameter("_format", "json")
            .addParameter("_marker", "0")
            .addParameter("p", "1")
            .addParameter("n", "100")
            .addParameter("ctx", "web6dot0")
            .build();
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "search results response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (!json.get("results").isNull() && !json.get("results").index(0).isNull()) {
                    List<AudioTrack> tracks = new ArrayList<>();
                    json.get("results").values()
                    .forEach(song -> {
                        if (!song.get("encrypted_media_url").isNull()) tracks.add(trackFactory.apply(buildInfo(song)));
                    });

                    return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
                }
                return null;
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load JioSaavn search results", SUSPICIOUS, e);
        }
    }

    private AudioTrackInfo buildInfo(JsonBrowser song) {
        return new AudioTrackInfo(
            song.get("song").text(),
            song.get("primary_artists").isList()
            ? song.get("primary_artists").index(0).text()
            : song.get("primary_artists").text(),
            (long) (song.get("duration").as(Double.class) * 1000.0),
            song.get("encrypted_media_url").text(),
            false,
            song.get("perma_url").text(),
            song.get("image").text().replace("150x150", "500x500")
        );
    }
}