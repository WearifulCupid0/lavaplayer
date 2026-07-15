package com.sedmelluq.lavaplayer.extensions.thirdpartysources.tidal;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.ThreadLocalHttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
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
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class TidalAudioSourceManager extends ThirdPartyAudioSourceManager implements HttpConfigurable {
    private static final String SEARCH_PREFIX = "tdsearch:";

    private static final Pattern TIDAL_URL_PATTERN = Pattern.compile(
            "^(?:https?://)?(?:www\\.|listen\\.)?tidal\\.com/(?:browse/)?" +
                    "(?<type>track|album|playlist|artist|video|mix)/(?<id>[A-Za-z0-9_-]+)(?:/.*)?(?:[?#].*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private final Map<String, String> isrcCache = new HashMap<>();

    private final boolean allowSearch;
    private final TidalTokenTracker tokenTracker;
    private final HttpInterfaceManager httpInterfaceManager;

    public TidalAudioSourceManager(AudioPlayerManager playerManager) {
        this(true, true, playerManager, null, null);
    }

    public TidalAudioSourceManager(AudioPlayerManager playerManager, String clientId, String clientSecret) {
        this(true, true, playerManager, clientId, clientSecret);
    }

    public TidalAudioSourceManager(String clientId, String clientSecret, AudioPlayerManager playerManager) {
        this(true, true, playerManager, clientId, clientSecret);
    }

    public TidalAudioSourceManager(boolean allowSearch, AudioPlayerManager playerManager) {
        this(allowSearch, true, playerManager, null, null);
    }

    public TidalAudioSourceManager(boolean allowSearch, AudioPlayerManager playerManager, String clientId, String clientSecret) {
        this(allowSearch, true, playerManager, clientId, clientSecret);
    }

    public TidalAudioSourceManager(boolean allowSearch, boolean fetchIsrc, AudioPlayerManager playerManager) {
        this(allowSearch, fetchIsrc, playerManager, null, null);
    }

    public TidalAudioSourceManager(
            boolean allowSearch,
            boolean fetchIsrc,
            AudioPlayerManager playerManager,
            String clientId,
            String clientSecret
    ) {
        super(playerManager, fetchIsrc);

        this.allowSearch = allowSearch;
        this.tokenTracker = new TidalTokenTracker(clientId, clientSecret);

        this.httpInterfaceManager = new ThreadLocalHttpInterfaceManager(
                HttpClientTools.createSharedCookiesHttpBuilder(),
                RequestConfig.custom()
                        .setConnectTimeout(10_000)
                        .setSocketTimeout(20_000)
                        .setConnectionRequestTimeout(10_000)
                        .build()
        );

        this.httpInterfaceManager.setHttpContextFilter(new TidalHttpContextFilter(this.tokenTracker));
    }

    public void setCredentials(String clientId, String clientSecret) {
        tokenTracker.setCredentials(clientId, clientSecret);
    }

    @Override
    public String getSourceName() {
        return "tidal";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference) {
        String identifier = reference.identifier;

        if (identifier == null || identifier.isBlank()) {
            return null;
        }

        if (identifier.regionMatches(true, 0, SEARCH_PREFIX, 0, SEARCH_PREFIX.length())) {
            if (!allowSearch) {
                return null;
            }

            return loadSearch(identifier.substring(SEARCH_PREFIX.length()).trim());
        }

        Matcher matcher = TIDAL_URL_PATTERN.matcher(identifier);

        if (!matcher.matches()) {
            return null;
        }

        String type = matcher.group("type").toLowerCase(Locale.ROOT);
        String id = matcher.group("id");

        switch (type) {
            case "track":
            case "video":
                return loadTrack(id);

            case "album":
                return loadAlbum(id);

            case "playlist":
                return loadPlaylist(id);

            case "artist":
                return loadArtistTracks(id);

            case "mix":
                return AudioReference.NO_TRACK;

            default:
                return null;
        }
    }

    @Override
    public void shutdown() {
        tokenTracker.shutdown();
    }

    @Override
    public String fetchIsrc(AudioTrack track) {
        if (track == null || track.getInfo() == null) {
            return null;
        }

        String identifier = track.getIdentifier();

        if (identifier == null || identifier.isBlank()) {
            return null;
        }

        if (isrcCache.containsKey(identifier)) {
            return isrcCache.get(identifier);
        }

        AudioItem item = loadTrack(identifier);

        if (item instanceof AudioTrack) {
            String isrc = isrcCache.get(((AudioTrack) item).getIdentifier());

            if (isrc != null && !isrc.isBlank()) {
                isrcCache.put(identifier, isrc);
                return isrc;
            }
        }

        return null;
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

    public TidalTokenTracker getTokenTracker() {
        return tokenTracker;
    }

    private AudioItem loadSearch(String query) {
        if (query == null || query.isBlank()) {
            return AudioReference.NO_TRACK;
        }

        try {
            JsonBrowser document = requestApi(TidalHelper.searchTracksUri(query));
            JsonBrowser data = document.get("data");

            if (data.index(0).isNull()) {
                return AudioReference.NO_TRACK;
            }

            List<String> trackIds = new ArrayList<>();

            for (JsonBrowser trackJson : data.values()) {
                String trackId = trackJson.get("id").text();
                if (!SourceTools.isBlank(trackId))
                    trackIds.add(trackId);
            }

            List<AudioTrack> tracks = loadIncludedTracks(document, trackIds);

            if (tracks.isEmpty()) {
                return AudioReference.NO_TRACK;
            }

            return BasicAudioPlaylist.createSearchResults(query, tracks);
        } catch (Exception e) {
            throw new FriendlyException("Failed to load TIDAL search result.", SUSPICIOUS, e);
        }
    }

    private AudioItem loadTrack(String id) {
        JsonBrowser document = requestApi(TidalHelper.trackUri(id));
        JsonBrowser track = firstDataItem(document);

        if (track.isNull()) {
            return AudioReference.NO_TRACK;
        }

        return TidalHelper.buildTrack(track, document.get("included"), this);
    }

    private AudioItem loadAlbum(String id) {
        JsonBrowser document = requestApi(TidalHelper.albumUri(id));
        JsonBrowser album = firstDataItem(document);

        if (album.isNull()) {
            return AudioReference.NO_TRACK;
        }

        JsonBrowser attributes = album.get("attributes");

        String title = firstNonBlank(
                attributes.get("title").text(),
                attributes.get("name").text()
        );

        List<String> trackIds = new ArrayList<>();

        for (JsonBrowser itemJson : album.get("relationships").get("items").get("data").values()) {
            String trackId = itemJson.get("id").text();
            if (!SourceTools.isBlank(trackId))
                trackIds.add(trackId);
        }

        List<AudioTrack> tracks = loadIncludedTracks(document, trackIds);

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        String artistId = album.get("relationships").get("artists").get("data").index(0).get("id").safeText();

        JsonBrowser artistJson = TidalHelper.findRelationship(document.get("included"), artistId, "artists");

        String artist = artistJson != null ? artistJson.get("attributes").get("name").text() : null;

        String artwork = tracks.get(0).getInfo().artworkUrl;

        return new BasicAudioPlaylist(
                title,
                artist,
                artwork,
                TidalConstants.ALBUM_URL + id,
                attributes.get("albumType").textOrDefault("album").toLowerCase(),
                tracks,
                null,
                false
        );
    }

    private AudioItem loadPlaylist(String id) {
        JsonBrowser document = requestApi(TidalHelper.playlistUri(id));
        JsonBrowser playlist = firstDataItem(document);

        if (playlist.isNull()) {
            return AudioReference.NO_TRACK;
        }

        JsonBrowser attributes = playlist.get("attributes");

        String title = firstNonBlank(
                attributes.get("title").text(),
                attributes.get("name").text()
        );

        List<String> trackIds = new ArrayList<>();

        for (JsonBrowser itemJson : playlist.get("relationships").get("items").get("data").values()) {
            String trackId = itemJson.get("id").text();
            if (!SourceTools.isBlank(trackId))
                trackIds.add(trackId);
        }

        List<AudioTrack> tracks = loadIncludedTracks(document, trackIds);

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        String creator = null;

        String ownerId = playlist.get("relationships").get("ownerProfiles").get("data").index(0).get("id").text();

        if (SourceTools.isBlank(ownerId))
            ownerId = playlist.get("relationships").get("collaboratorProfiles").get("data").index(0).get("id").text();

        if (!SourceTools.isBlank(ownerId)) {
            JsonBrowser ownerJson = TidalHelper.findRelationship(document.get("included"), ownerId, "artists");
            if (ownerJson != null)
                creator = firstNonBlank(
                        ownerJson.get("attributes").get("name").text(),
                        ownerJson.get("attributes").get("title").text()
                );
        }

        String pictureUrl = null;

        String pictureId = playlist.get("relationships").get("coverArt").get("data").index(0).get("id").text();
        if (!SourceTools.isBlank(pictureId)) {
            JsonBrowser pictureJson = TidalHelper.findRelationship(document.get("included"), pictureId, "artworks");
            if (pictureJson != null)
                pictureUrl = TidalHelper.findBestArtwork(pictureJson);
        }

        return new BasicAudioPlaylist(
                title,
                creator,
                pictureUrl,
                TidalConstants.PLAYLIST_URL + id,
                "playlist",
                tracks,
                null,
                false
        );
    }

    private AudioItem loadArtistTracks(String id) {
        JsonBrowser document = requestApi(TidalHelper.artistTracksUri(id));
        JsonBrowser artist = firstDataItem(document);

        if (artist.isNull()) {
            return AudioReference.NO_TRACK;
        }

        JsonBrowser attributes = artist.get("attributes");

        List<AudioTrack> tracks = loadIncludedTracks(document);

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        String name = attributes.get("name").text();

        String pictureUrl = null;

        String pictureId = artist.get("relationships").get("profileArt").get("data").index(0).get("id").text();
        if (!SourceTools.isBlank(pictureId)) {
            JsonBrowser pictureJson = TidalHelper.findRelationship(document.get("included"), pictureId, "artworks");
            if (pictureJson != null)
                pictureUrl = TidalHelper.findBestArtwork(pictureJson);
        }

        return new BasicAudioPlaylist(
                name,
                name,
                pictureUrl,
                TidalConstants.ARTIST_URL + id,
                "artist",
                tracks,
                null,
                false
        );
    }

    private List<AudioTrack> loadIncludedTracks(JsonBrowser document) {
        return loadIncludedTracks(document, new ArrayList<>());
    }

    private List<AudioTrack> loadIncludedTracks(JsonBrowser document, List<String> trackIds) {
        List<AudioTrack> tracks = new ArrayList<>();

        if (!trackIds.isEmpty()) {
            JsonBrowser included = document;
            if (!included.get("included").isNull())
                included = document.get("included");

            for (String trackId : trackIds) {
                JsonBrowser trackJson = TidalHelper.findRelationship(included, trackId, "tracks");
                if (trackJson != null) {
                    AudioTrack track = TidalHelper.buildTrack(trackJson, included, this);
                    tracks.add(track);
                }
            }
        } else {
            for (JsonBrowser included : document.get("included").values()) {
                String type = included.get("type").text();

                if ("tracks".equals(type) || "track".equals(type)) {
                    AudioTrack track = TidalHelper.buildTrack(included, document.get("included"), this);

                    tracks.add(track);
                }
            }
        }

        return tracks;
    }

    private JsonBrowser requestApi(URI uri) {
        try (HttpInterface httpInterface = getHttpInterface();
             CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
            int statusCode = response.getStatusLine().getStatusCode();

            String responseText = response.getEntity() != null
                    ? IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8)
                    : "";

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("TIDAL v2 API request failed with status code " + statusCode + ": " + responseText);
            }

            return JsonBrowser.parse(responseText);
        } catch (Exception e) {
            throw new FriendlyException("Failed to make a request to TIDAL v2 API.", SUSPICIOUS, e);
        }
    }

    private JsonBrowser firstDataItem(JsonBrowser document) {
        JsonBrowser first = document.get("data").index(0);

        if (!first.isNull()) {
            return first;
        }

        return JsonBrowser.NULL_BROWSER;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }
}