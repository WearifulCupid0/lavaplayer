package com.sedmelluq.lavaplayer.source.audiomack;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudiomackAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String AUDIOMACK_URL_REGEX = "^(?:https?://)?(?:www\\.)?audiomack\\.com/(?<artistSlug>[^/?#]+)/(?<contentType>song|album|playlist)/(?<contentSlug>[^/?#]+)/?(?:[?#].*)?$";
    private static final String AUDIOMACK_ARTIST_REGEX = "^(?:https?://)?(?:www\\.)?audiomack\\.com/(?<artistSlug>[^/?#]+)/?(?:[?#].*)?$";

    private static final Pattern urlPattern = Pattern.compile(AUDIOMACK_URL_REGEX, Pattern.CASE_INSENSITIVE);
    private static final Pattern artistPattern = Pattern.compile(AUDIOMACK_ARTIST_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String SEARCH_PREFIX = "amsearch:";

    private final AudiomackTokenTracker tokenTracker;
    private final AudiomackHttpContextFilter contextFilter;

    private final boolean allowSearch;
    private final HttpInterfaceManager httpInterfaceManager;
    private final HttpAudioSourceManager streamSourceManager;

    public AudiomackAudioSourceManager() {
        this(true, new HttpAudioSourceManager());
    }

    public AudiomackAudioSourceManager(boolean allowSearch) {
        this(null, null, allowSearch, new HttpAudioSourceManager());
    }

    public AudiomackAudioSourceManager(boolean allowSearch, HttpAudioSourceManager streamSourceManager) {
        this(null, null, allowSearch, streamSourceManager);
    }

    public AudiomackAudioSourceManager(String consumerKey, String consumerSecret, boolean allowSearch, HttpAudioSourceManager streamSourceManager) {
        this.tokenTracker = new AudiomackTokenTracker(consumerKey, consumerSecret);
        this.contextFilter = new AudiomackHttpContextFilter(tokenTracker);

        this.allowSearch = allowSearch;
        this.streamSourceManager = streamSourceManager;

        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();

        httpInterfaceManager.setHttpContextFilter(contextFilter);
    }

    @Override
    public String getSourceName() {
        return "audiomack";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if (reference.identifier.startsWith(SEARCH_PREFIX) && allowSearch) {
            return loadSearch(reference.identifier.substring(SEARCH_PREFIX.length()));
        }

        Matcher matcher = urlPattern.matcher(reference.identifier);
        if (matcher.matches()) {
            String artistSlug = matcher.group("artistSlug").trim();
            String contentSlug = matcher.group("contentSlug").trim();
            switch (matcher.group("contentType").trim().toLowerCase()) {
                case "song": return loadTrack(artistSlug, contentSlug);
                case "album": return loadAlbum(artistSlug, contentSlug);
                case "playlist": return loadPlaylist(artistSlug, contentSlug);
            }
        }

        matcher = artistPattern.matcher(reference.identifier);
        if (matcher.matches()) {
            String artistSlug = matcher.group("artistSlug").trim();
            return loadArtist(artistSlug);
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
        return new AudiomackAudioTrack(trackInfo, this);
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

    public AudioItem loadStream(AudioReference reference) {
        return streamSourceManager.loadItem(null, reference);
    }

    private AudioTrack loadTrack(String artistSlug, String songSlug) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI requestUri = URI.create(String.format(AudiomackConstants.AUDIOMACK_SONG_RESOLVE, artistSlug, songSlug));
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(requestUri))) {
                HttpClientTools.assertSuccessWithContent(response, "audiomack api track resolve");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (json.get("results").isNull()) return null;

                return buildTrack(json.get("results"));
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed do load Audiomack track", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private AudioPlaylist loadAlbum(String artistSlug, String albumSlug) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI requestUri = URI.create(String.format(AudiomackConstants.AUDIOMACK_ALBUM_RESOLVE, artistSlug, albumSlug));
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(requestUri))) {
                HttpClientTools.assertSuccessWithContent(response, "audiomack api album resolve");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (json.get("results").isNull() || json.get("results").get("tracks").isNull() || !json.get("results").get("tracks").isList()) return null;

                json = json.get("results");

                List<AudioTrack> tracks = new ArrayList<>();

                for (JsonBrowser trackJson : json.get("tracks").values()) {
                    tracks.add(buildTrack(trackJson, json.get("uploader")));
                }

                return new BasicAudioPlaylist(
                        json.get("title").text(),
                        json.get("uploader").get("name").text(),
                        json.get("image").textOrDefault(json.get("image_base").text()),
                        String.format(AudiomackConstants.AUDIOMACK_ALBUM_URL, artistSlug, albumSlug),
                        "album",
                        tracks,
                        null,
                        false
                );
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed do load Audiomack album", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private AudioPlaylist loadPlaylist(String userSlug, String playlistSlug) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI requestUri = URI.create(String.format(AudiomackConstants.AUDIOMACK_PLAYLIST_RESOLVE, userSlug, playlistSlug));
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(requestUri))) {
                HttpClientTools.assertSuccessWithContent(response, "audiomack api playlist resolve");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (json.get("results").isNull() || json.get("results").get("tracks").isNull() || !json.get("results").get("tracks").isList()) return null;

                json = json.get("results");

                List<AudioTrack> tracks = new ArrayList<>();

                for (JsonBrowser trackJson : json.get("tracks").values()) {
                    tracks.add(buildTrack(trackJson));
                }

                return new BasicAudioPlaylist(
                        json.get("title").text(),
                        json.get("artist").get("name").text(),
                        json.get("image").textOrDefault(json.get("image_base").text()),
                        String.format(AudiomackConstants.AUDIOMACK_PLAYLIST_URL, userSlug, playlistSlug),
                        "playlist",
                        json.get("track_count").asInt(tracks.size()),
                        tracks,
                        null,
                        false
                );
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed do load Audiomack playlist", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private AudioPlaylist loadArtist(String artistSlug) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI requestUri = URI.create(String.format(AudiomackConstants.AUDIOMACK_ARTIST_RESOLVE, artistSlug));
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(requestUri))) {
                HttpClientTools.assertSuccessWithContent(response, "audiomack api artist resolve");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (json.get("results").isNull() || !json.get("results").isList()) return null;

                json = json.get("results");

                List<AudioTrack> tracks = new ArrayList<>();

                JsonBrowser artistJson = null;

                for (JsonBrowser resultJson : json.values()) {
                    if (!resultJson.get("uploader").isNull() || artistJson != null) {
                        if (artistJson == null) artistJson = resultJson.get("uploader");

                        String type = resultJson.get("type").safeText().trim().toLowerCase();
                        if ("track".equals(type)) {
                            tracks.add(buildTrack(resultJson, artistJson));
                        } else if ("album".equals(type)) {
                            for (JsonBrowser trackData : resultJson.get("tracks").values()) {
                                tracks.add(buildTrack(trackData, artistJson));
                            }
                        } else if ("playlist".equals(type)) {
                            for (JsonBrowser trackData : resultJson.get("tracks").values()) {
                                tracks.add(buildTrack(trackData));
                            }
                        }
                    }
                }

                if (artistSlug == null)
                    return null;

                return new BasicAudioPlaylist(
                        artistJson.get("name").text(),
                        artistJson.get("name").text(),
                        artistJson.get("image").textOrDefault(artistJson.get("image_base").text()),
                        String.format(AudiomackConstants.AUDIOMACK_ARTIST_URL, artistSlug),
                        "artist",
                        tracks,
                        null,
                        false
                );
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed do load Audiomack artist uploads", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private AudioPlaylist loadSearch(String query) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI requestUri = buildSearchURI(query);
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(requestUri))) {
                HttpClientTools.assertSuccessWithContent(response, "audiomack api search results");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                if (json.get("results").isNull() || !json.get("results").isList())
                    return null;

                List<AudioTrack> tracks = new ArrayList<>();

                for (JsonBrowser trackJson : json.get("results").values()) {
                    tracks.add(buildTrack(trackJson));
                }

                return BasicAudioPlaylist.createSearchResults(query, tracks);
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed do load Audiomack search results", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private URI buildSearchURI(String query) throws URISyntaxException {
        return
                new URIBuilder(AudiomackConstants.AUDIOMACK_SEARCH)
                        .addParameter("q", query)
                        .addParameter("show", "songs")
                        .build();
    }

    private AudioTrack buildTrack(JsonBrowser trackJson) {
        return buildTrack(trackJson, trackJson.get("uploader"));
    }

    private AudioTrack buildTrack(JsonBrowser trackJson, JsonBrowser uploaderJson) {
        String uploaderName = uploaderJson.get("name").text();
        String uploaderUrl = String.format(AudiomackConstants.AUDIOMACK_ARTIST_URL, uploaderJson.get("url_slug").text());

        AudioTrackAuthorInfo authorInfo = new AudioTrackAuthorInfo(uploaderName, uploaderUrl);

        AudioTrackInfo trackInfo = new AudioTrackInfo(
                trackJson.get("title").text(),
                authorInfo,
                (long) (trackJson.get("duration").as(Double.class) * 1000.0),
                trackJson.get("id").text(),
                false,
                String.format(AudiomackConstants.AUDIOMACK_SONG_URL, uploaderJson.get("url_slug").text(), trackJson.get("url_slug").text()),
                trackJson.get("image").textOrDefault(trackJson.get("image_base").text()),
                trackJson.get("explicit").safeText().equals("yes"),
                trackJson.get("isrc").text()
        );

        return new AudiomackAudioTrack(trackInfo, this);
    }
}
