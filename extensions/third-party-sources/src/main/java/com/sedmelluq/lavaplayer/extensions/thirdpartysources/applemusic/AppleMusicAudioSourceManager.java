package com.sedmelluq.lavaplayer.extensions.thirdpartysources.applemusic;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.ThreadLocalHttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.SourceTools;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.ThirdPartyAudioSourceManager;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.ThirdPartyAudioTrack;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;
import static com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN;

public class AppleMusicAudioSourceManager extends ThirdPartyAudioSourceManager implements HttpConfigurable {
    private static final String BASE_URL = "https://api.music.apple.com";
    private static final String DEFAULT_STOREFRONT = "us";

    private static final String SEARCH_PREFIX = "amsearch:";

    private static final Pattern APPLE_MUSIC_URL_PATTERN = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?music\\.apple\\.com/" +
                    "(?<storefront>[a-zA-Z]{2})/" +
                    "(?<type>artist|playlist|album|music-video)/" +
                    "(?:[^/?#]+/)?" +
                    "(?<id>[A-Za-z0-9._-]+)" +
                    "(?:\\?(?<query>[^#]*))?" +
                    "(?:#.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private final boolean allowSearch;
    private final AppleMusicTokenTracker tokenTracker;
    private final HttpInterfaceManager httpInterfaceManager;

    public AppleMusicAudioSourceManager(AudioPlayerManager playerManager) {
        this(true, playerManager);
    }

    public AppleMusicAudioSourceManager(boolean allowSearch, AudioPlayerManager playerManager) {
        super(playerManager);

        this.allowSearch = allowSearch;

        this.httpInterfaceManager = new ThreadLocalHttpInterfaceManager(
                HttpClientTools.createSharedCookiesHttpBuilder(),
                RequestConfig.custom()
                        .setConnectTimeout(10_000)
                        .setSocketTimeout(20_000)
                        .setConnectionRequestTimeout(10_000)
                        .build()
        );

        this.tokenTracker = new AppleMusicTokenTracker(httpInterfaceManager);
        this.httpInterfaceManager.setHttpContextFilter(new AppleMusicHttpContextFilter(this.tokenTracker));
    }

    @Override
    public String getSourceName() {
        return "apple-music";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference) {
        String identifier = reference.identifier;

        if (identifier == null) {
            return null;
        }

        if (identifier.regionMatches(true, 0, SEARCH_PREFIX, 0, SEARCH_PREFIX.length())) {
            if (!allowSearch) {
                return null;
            }

            return loadSearch(DEFAULT_STOREFRONT, identifier.substring(SEARCH_PREFIX.length()).trim());
        }

        AppleMusicUrl appleMusicUrl = parseUrl(identifier);

        if (appleMusicUrl == null) {
            return null;
        }

        switch (appleMusicUrl.type) {
            case "album":
                if (appleMusicUrl.trackId != null && !appleMusicUrl.trackId.isEmpty()) {
                    return loadTrack(appleMusicUrl.storefront, appleMusicUrl.trackId);
                }

                return loadAlbum(appleMusicUrl.storefront, appleMusicUrl.id);

            case "playlist":
                return loadPlaylist(appleMusicUrl.storefront, appleMusicUrl.id);

            case "artist":
                return loadArtist(appleMusicUrl.storefront, appleMusicUrl.id);

            case "music-video":
                return loadMusicVideo(appleMusicUrl.storefront, appleMusicUrl.id);

            default:
                return null;
        }
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void shutdown() {
        // Nothing to shutdown.
    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
    }

    public AppleMusicTokenTracker getTokenTracker() {
        return tokenTracker;
    }

    private AudioItem loadSearch(String storefront, String query) {
        if (query == null || query.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        try {
            URI uri = new URIBuilder(searchUri(storefront))
                    .addParameter("limit", "25")
                    .addParameter("term", query)
                    .addParameter("types", "songs")
                    .build();

            JsonBrowser json = requestApi(uri);
            JsonBrowser data = json.get("results").get("songs").get("data");

            if (data.index(0).isNull()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();

            for (JsonBrowser track : data.values()) {
                tracks.add(buildTrack(track));
            }

            return BasicAudioPlaylist.createSearchResults(query, tracks);
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Apple Music search result.", SUSPICIOUS, e);
        }
    }

    private AudioItem loadTrack(String storefront, String trackId) {
        JsonBrowser track = requestApi(trackUri(storefront, trackId)).get("data").index(0);

        if (track.isNull()) {
            return AudioReference.NO_TRACK;
        }

        return buildTrack(track);
    }

    private AudioItem loadMusicVideo(String storefront, String videoId) {
        JsonBrowser video = requestApi(musicVideoUri(storefront, videoId)).get("data").index(0);

        if (video.isNull()) {
            return AudioReference.NO_TRACK;
        }

        return buildTrack(video);
    }

    private AudioItem loadPlaylist(String storefront, String playlistId) {
        JsonBrowser playlist = requestApi(playlistUri(storefront, playlistId)).get("data").index(0);

        if (playlist.isNull()) {
            return AudioReference.NO_TRACK;
        }

        JsonBrowser attributes = playlist.get("attributes");
        List<AudioTrack> tracks = new ArrayList<>();

        JsonBrowser tracksJson = playlist.get("relationships").get("tracks");
        appendTracks(tracks, tracksJson);

        String next = tracksJson.get("next").text();

        while (next != null && !next.isEmpty()) {
            tracksJson = requestApi(resolveNextUri(next));
            appendTracks(tracks, tracksJson);
            next = tracksJson.get("next").text();
        }

        return new BasicAudioPlaylist(
                attributes.get("name").safeText(),
                attributes.get("curatorName").safeText(),
                formatArtworkUrl(attributes.get("artwork"), "800", "800"),
                attributes.get("url").text(),
                "playlist",
                tracks,
                null,
                false
        );
    }

    private AudioItem loadAlbum(String storefront, String albumId) {
        JsonBrowser album = requestApi(albumUri(storefront, albumId)).get("data").index(0);

        if (album.isNull()) {
            return AudioReference.NO_TRACK;
        }

        JsonBrowser attributes = album.get("attributes");
        List<AudioTrack> tracks = new ArrayList<>();

        JsonBrowser tracksJson = album.get("relationships").get("tracks");
        appendTracks(tracks, tracksJson);

        String next = tracksJson.get("next").text();

        while (next != null && !next.isEmpty()) {
            tracksJson = requestApi(resolveNextUri(next));
            appendTracks(tracks, tracksJson);
            next = tracksJson.get("next").text();
        }

        return new BasicAudioPlaylist(
                attributes.get("name").safeText(),
                attributes.get("artistName").safeText(),
                formatArtworkUrl(attributes.get("artwork"), "800", "800"),
                attributes.get("url").text(),
                attributes.get("isSingle").asBoolean(false) ? "single" : "album",
                tracks,
                null,
                false
        );
    }

    private AudioItem loadArtist(String storefront, String artistId) {
        JsonBrowser artist = requestApi(artistUri(storefront, artistId)).get("data").index(0).get("attributes");

        if (artist.isNull()) {
            return AudioReference.NO_TRACK;
        }

        JsonBrowser json = requestApi(artistTopSongsUri(storefront, artistId));

        if (json.get("data").index(0).isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();

        for (JsonBrowser track : json.get("data").values()) {
            tracks.add(buildTrack(track));
        }

        return new BasicAudioPlaylist(
                artist.get("name").safeText(),
                artist.get("name").safeText(),
                null,
                artist.get("url").safeText(),
                "artist",
                tracks,
                null,
                false
        );
    }

    private void appendTracks(List<AudioTrack> tracks, JsonBrowser tracksJson) {
        for (JsonBrowser track : tracksJson.get("data").values()) {
            tracks.add(buildTrack(track));
        }
    }

    private AudioTrack buildTrack(JsonBrowser trackInfo) {
        JsonBrowser attributes = trackInfo.get("attributes");

        String identifier = trackInfo.get("id").text();
        String title = attributes.get("name").safeText();
        String artistName = attributes.get("artistName").safeText();
        String artistUrl = null;
        String artistId = trackInfo.get("relationships").get("artists").get("data").index(0).get("id").text();
        if (!SourceTools.isBlank(artistId))
            artistUrl = "https://music.apple.com/artist/" + artistId;
        long duration = attributes.get("durationInMillis").asLong(DURATION_MS_UNKNOWN);
        String uri = attributes.get("url").text();
        String artworkUrl = formatArtworkUrl(attributes.get("artwork"), "800", "800");
        boolean explicit = "explicit".equalsIgnoreCase(attributes.get("contentRating").safeText());
        String isrc = attributes.get("isrc").text();

        AudioTrackInfo info = new AudioTrackInfo(
                title,
                new AudioTrackAuthorInfo(artistName, artistUrl),
                duration,
                identifier,
                false,
                uri,
                artworkUrl,
                explicit,
                isrc
        );

        return new ThirdPartyAudioTrack(info, this);
    }

    private JsonBrowser requestApi(String uri) {
        return requestApi(URI.create(uri));
    }

    private JsonBrowser requestApi(URI uri) {
        try (HttpInterface httpInterface = getHttpInterface();
             CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("Apple Music API request failed with status code: " + statusCode);
            }

            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            return JsonBrowser.parse(responseText);
        } catch (IOException e) {
            throw new FriendlyException("Failed to make a request to Apple Music API.", SUSPICIOUS, e);
        }
    }

    private static AppleMusicUrl parseUrl(String identifier) {
        Matcher matcher = APPLE_MUSIC_URL_PATTERN.matcher(identifier);

        if (!matcher.matches()) {
            return null;
        }

        String storefront = matcher.group("storefront").toLowerCase(Locale.ROOT);
        String type = matcher.group("type").toLowerCase(Locale.ROOT);
        String id = matcher.group("id");
        String query = matcher.group("query");

        String trackId = getQueryParameter(query, "i");

        return new AppleMusicUrl(storefront, type, id, trackId);
    }

    private static String getQueryParameter(String query, String name) {
        if (query == null || query.isEmpty()) {
            return null;
        }

        String[] parts = query.split("&");

        for (String part : parts) {
            int separator = part.indexOf('=');

            if (separator <= 0) {
                continue;
            }

            String key = urlDecode(part.substring(0, separator));
            String value = urlDecode(part.substring(separator + 1));

            if (name.equals(key)) {
                return value;
            }
        }

        return null;
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String formatArtworkUrl(JsonBrowser artwork, String defaultWidth, String defaultHeight) {
        if (artwork == null || artwork.isNull()) {
            return null;
        }

        String url = artwork.get("url").text();

        if (url == null || url.isEmpty()) {
            return null;
        }

        String width = artwork.get("width").text();

        if (width == null || width.isEmpty()) {
            width = defaultWidth;
        }

        String height = artwork.get("height").text();

        if (height == null || height.isEmpty()) {
            height = defaultHeight;
        }

        return url.replace("{w}x{h}", width + "x" + height);
    }

    private static String resolveNextUri(String next) {
        if (next.startsWith("http://") || next.startsWith("https://")) {
            return next;
        }

        return BASE_URL + next;
    }

    private static String catalogBase(String storefront) {
        return BASE_URL + "/v1/catalog/" + storefront;
    }

    private static String searchUri(String storefront) {
        return catalogBase(storefront) + "/search";
    }

    private static String trackUri(String storefront, String trackId) {
        return catalogBase(storefront) + "/songs/" + trackId;
    }

    private static String musicVideoUri(String storefront, String videoId) {
        return catalogBase(storefront) + "/music-videos/" + videoId;
    }

    private static String playlistUri(String storefront, String playlistId) {
        return catalogBase(storefront) + "/playlists/" + playlistId;
    }

    private static String albumUri(String storefront, String albumId) {
        return catalogBase(storefront) + "/albums/" + albumId;
    }

    private static String artistUri(String storefront, String artistId) {
        return catalogBase(storefront) + "/artists/" + artistId;
    }

    private static String artistTopSongsUri(String storefront, String artistId) {
        return artistUri(storefront, artistId) + "/view/top-songs";
    }

    private static class AppleMusicUrl {
        private final String storefront;
        private final String type;
        private final String id;
        private final String trackId;

        private AppleMusicUrl(String storefront, String type, String id, String trackId) {
            this.storefront = storefront;
            this.type = type;
            this.id = id;
            this.trackId = trackId;
        }
    }
}