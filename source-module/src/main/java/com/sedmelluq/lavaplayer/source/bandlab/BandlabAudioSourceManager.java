package com.sedmelluq.lavaplayer.source.bandlab;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class BandlabAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String SEARCH_PREFIX = "blsearch:";

    private final boolean allowSearch;

    private final BandlabTokenTracker tokenTracker;
    private final HttpInterfaceManager httpInterfaceManager;

    public BandlabAudioSourceManager() {
        this(System.getProperty("BANDLAB_REFRESH_TOKEN", System.getenv("BANDLAB_REFRESH_TOKEN")), true);
    }

    public BandlabAudioSourceManager(String refreshToken) {
        this(refreshToken, true);
    }

    public BandlabAudioSourceManager(String refreshToken, boolean allowSearch) {
        this.allowSearch = allowSearch;

        this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();

        this.tokenTracker = new BandlabTokenTracker(refreshToken, httpInterfaceManager);

        this.httpInterfaceManager.setHttpContextFilter(new BandlabHttpContextFilter(this.tokenTracker));
    }

    @Override
    public String getSourceName() {
        return "bandlab";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if (reference.identifier.startsWith(SEARCH_PREFIX) && allowSearch) {
            return loadSearch(reference.identifier.substring(SEARCH_PREFIX.length()));
        }

        BandlabUrlParser.BandlabUrlData data = BandlabUrlParser.parse(reference.identifier);

        if (data != null) {
            switch(data.getType()) {
                case ARTIST: return loadArtist(data.getArtistSlug());
                case ALBUM: return loadAlbum(data.getId());
                case PLAYLIST: return loadPlaylist(data.getId());
                case SONG: return loadSong(data.getId());
                case REVISION: return loadRevision(data.getId());
                case POST: return loadPost(data.getId());
            }
        }

        return null;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {

    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new BandlabAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    private AudioTrack loadRevision(String id) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI requestUri = URI.create(String.format(BandlabConstants.BANDLAB_API_REVISION, id));
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(requestUri))) {
                HttpClientTools.assertSuccessWithContent(response, "bandlab api revision results");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                if (!isPlayableTrack(json)) return null;

                return buildTrack(json.get("song"), json);
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Bandlab track results", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private AudioTrack loadSong(String id) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI requestUri = URI.create(String.format(BandlabConstants.BANDLAB_API_TRACK, id));
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(requestUri))) {
                HttpClientTools.assertSuccessWithContent(response, "bandlab api song results");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                if (!isPlayableTrack(json.get("revision"))) return null;

                return buildTrack(json, json.get("revision"));
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Bandlab track results", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private AudioTrack loadPost(String id) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI requestUri = URI.create(String.format(BandlabConstants.BANDLAB_API_POST, id));
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(requestUri))) {
                HttpClientTools.assertSuccessWithContent(response, "bandlab api post results");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                if (!isPlayableTrack(json.get("track"))) return null;

                return buildTrackPost(json);
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Bandlab track results", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private AudioPlaylist loadAlbum(String id) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI requestUri = URI.create(String.format(BandlabConstants.BANDLAB_API_ALBUM, id));
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(requestUri))) {
                HttpClientTools.assertSuccessWithContent(response, "bandlab api album results");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                List<AudioTrack> tracks = new ArrayList<>();

                if (
                        json.get("posts").isNull() ||
                        !json.get("posts").isList() ||
                        json.get("posts").values().isEmpty()
                ) return null;

                for (JsonBrowser trackJson : json.get("posts").values())
                    if (isPlayableTrack(trackJson.get("track")))
                        tracks.add(buildTrackPost(trackJson));

                return new BasicAudioPlaylist(
                        json.get("name").text(),
                        json.get("creator").get("name").text(),
                        json.get("picture").get("url").text(),
                        String.format(BandlabConstants.ALBUM_URL, json.get("creator").get("username").text(), id),
                        json.get("type").textOrDefault("album").toLowerCase(),
                        tracks,
                        null,
                        false
                );
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Bandlab album results", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private AudioPlaylist loadPlaylist(String id) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI requestUri = URI.create(String.format(BandlabConstants.BANDLAB_API_PLAYLIST, id));
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(requestUri))) {
                HttpClientTools.assertSuccessWithContent(response, "bandlab api playlist results");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                List<AudioTrack> tracks = new ArrayList<>();

                if (
                        json.get("posts").isNull() ||
                        !json.get("posts").isList() ||
                        json.get("posts").values().isEmpty()
                ) return null;

                for (JsonBrowser trackJson : json.get("posts").values())
                    if (isPlayableTrack(trackJson.get("revision")))
                        tracks.add(buildTrack(trackJson.get("revision").get("song"), trackJson.get("revision")));

                return new BasicAudioPlaylist(
                        json.get("name").text(),
                        json.get("creator").get("name").text(),
                        json.get("picture").get("url").text(),
                        String.format(BandlabConstants.PLAYLIST_URL, json.get("creator").get("username").text(), id),
                        "playlist",
                        tracks,
                        null,
                        false
                );
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Bandlab playlist results", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private AudioPlaylist loadArtist(String username) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI userURL = URI.create(String.format(BandlabConstants.BANDLAB_API_USER, username));
            try (CloseableHttpResponse userResponse = httpInterface.execute(new HttpGet(userURL))) {
                HttpClientTools.assertSuccessWithContent(userResponse, "bandlab api artist results");

                JsonBrowser userData = JsonBrowser.parse(userResponse.getEntity().getContent());

                String id = userData.get("id").text();
                if (id == null || id.isBlank()) return null;

                URI tracksURL = URI.create(String.format(BandlabConstants.BANDLAB_API_USER_TRACKS, id));
                try (CloseableHttpResponse tracksResponse = httpInterface.execute(new HttpGet(tracksURL))) {
                    HttpClientTools.assertSuccessWithContent(tracksResponse, "bandlab api artist tracks results");

                    JsonBrowser tracksData = JsonBrowser.parse(tracksResponse.getEntity().getContent());

                    if (
                            tracksData.get("data").isNull() ||
                            !tracksData.get("data").isList() ||
                            tracksData.get("data").values().isEmpty()
                    ) return null;

                    List<AudioTrack> tracks = new ArrayList<>();

                    for (JsonBrowser post : tracksData.get("data").values())
                        tracks.add(buildTrackPost(post));

                    return new BasicAudioPlaylist(
                            userData.get("name").text(),
                            userData.get("name").text(),
                            userData.get("picture").get("url").text(),
                            String.format(BandlabConstants.ARTIST_URL, username),
                            "artist",
                            tracks,
                            null,
                            false
                    );
                }
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Bandlab artist results", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private AudioPlaylist loadSearch(String query) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI requestUri = buildSearchURI(query);
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(requestUri))) {
                HttpClientTools.assertSuccessWithContent(response, "bandlab api search results");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                if (
                        json.get("data").isNull() ||
                        !json.get("data").isList() ||
                        json.get("data").values().isEmpty()
                ) return null;

                List<AudioTrack> tracks = new ArrayList<>();

                for (JsonBrowser data : json.get("data").values()) {
                    if (isPlayableTrack(data.get("revision")))
                        tracks.add(buildTrack(data, data.get("revision")));
                }

                return BasicAudioPlaylist.createSearchResults(query, tracks);
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Bandlab search results", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private URI buildSearchURI(String query) throws URISyntaxException {
        return new URIBuilder(BandlabConstants.BANDLAB_API_SEARCH)
                .addParameter("limit", "20")
                .addParameter("query", query)
                .build();
    }

    private boolean isPlayableTrack(JsonBrowser revision) {
        String file = revision.get("mixdown").get("file").text();

        if (file != null && !file.isBlank()) return true;

        file = revision.get("sample").get("audioUrl").text();
        return file != null && !file.isBlank();
    }

    private String getPlaybackUrl(JsonBrowser revision) {
        return revision.get("mixdown").get("file").textOrDefault(revision.get("sample").get("audioUrl").textOrDefault(revision.get("audioUrl").text()));
    }

    private JsonBrowser getMixdownSample(JsonBrowser json) {
        if (json.get("mixdown").isNull()) return json.get("sample");
        return json.get("mixdown");
    }

    private AudioTrack buildTrackPost(JsonBrowser json) {

        AudioTrackAuthorInfo creator =
                new AudioTrackAuthorInfo(
                        json.get("creator").get("name").text(),
                        String.format(BandlabConstants.ARTIST_URL,  json.get("creator").get("username").text())
                );

        AudioTrackInfo trackInfo = new AudioTrackInfo(
                json.get("track").get("name").text(),
                creator,
                (long) (getMixdownSample(json.get("track")).get("duration").as(Double.class) * 1000.0),
                getPlaybackUrl(json.get("track")),
                false,
                String.format(BandlabConstants.TRACK_URL, json.get("id").text()),
                json.get("track").get("picture").get("url").text()
        );

        return new BandlabAudioTrack(trackInfo, this);
    }

    private AudioTrack buildTrack(JsonBrowser json, JsonBrowser revision) {
        List<AudioTrackAuthorInfo> artists = new ArrayList<>();

        if (json.get("collaborators").isList()) {
            for (JsonBrowser collaborator : json.get("collaborators").values()) {
                artists.add(
                        new AudioTrackAuthorInfo(
                                collaborator.get("name").text(),
                                String.format(BandlabConstants.ARTIST_URL, collaborator.get("username").text())
                        )
                );
            }
        } else {
            JsonBrowser author = !json.get("author").isNull() ? json.get("author") : revision.get("creator");
            artists.add(
                    new AudioTrackAuthorInfo(
                            author.get("name").text(),
                            String.format(BandlabConstants.ARTIST_URL, author.get("username").text())
                    )
            );
        }

        AudioTrackInfo trackInfo = new AudioTrackInfo(
                json.get("name").text(),
                artists,
                (long) (getMixdownSample(revision).get("duration").as(Double.class) * 1000.0),
                getPlaybackUrl(revision),
                false,
                String.format(BandlabConstants.TRACK_URL, revision.get("id").text()),
                json.get("picture").get("url").text()
        );

        return new BandlabAudioTrack(trackInfo, this);
    }
}
