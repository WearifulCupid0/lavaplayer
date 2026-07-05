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
    private static final String DEFAULT_COUNTRY_CODE = "US";
    private static final String DEFAULT_LOCALE = "en-US";

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
                return loadArtistTopTracks(id);

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
            JsonBrowser document = requestApi(searchTracksUri(query));
            JsonBrowser data = document.get("data");

            if (data.index(0).isNull()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();

            for (JsonBrowser item : data.values()) {
                AudioTrack track = buildTrackFromReferenceOrResource(item, document);

                if (track != null) {
                    tracks.add(track);
                }
            }

            if (tracks.isEmpty()) {
                return AudioReference.NO_TRACK;
            }

            return BasicAudioPlaylist.createSearchResults(query, tracks);
        } catch (Exception e) {
            throw new FriendlyException("Failed to load TIDAL search result.", SUSPICIOUS, e);
        }
    }

    private AudioItem loadTrack(String id) {
        JsonBrowser document = requestApi(trackUri(id));
        JsonBrowser track = firstDataItem(document);

        if (track.isNull()) {
            return AudioReference.NO_TRACK;
        }

        AudioTrack audioTrack = buildTrack(track, document);

        return audioTrack != null ? audioTrack : AudioReference.NO_TRACK;
    }

    private AudioItem loadAlbum(String id) {
        JsonBrowser document = requestApi(albumUri(id));
        JsonBrowser album = firstDataItem(document);

        if (album.isNull()) {
            return AudioReference.NO_TRACK;
        }

        JsonBrowser attributes = album.get("attributes");

        String title = firstNonBlank(
                attributes.get("title").text(),
                attributes.get("name").text(),
                "Unknown album"
        );

        String artist = firstNonBlank(
                firstIncludedAttribute(document, "artists", "name"),
                "TIDAL"
        );

        String artwork = firstArtwork(document);

        List<AudioTrack> tracks = new ArrayList<>(loadIncludedTracks(document));

        String relatedItems = firstNonBlank(
                album.get("relationships").get("items").get("links").get("related").text(),
                album.get("relationships").get("tracks").get("links").get("related").text()
        );

        if (tracks.isEmpty() && relatedItems != null) {
            tracks.addAll(loadRelationshipTracks(relatedItems));
        }

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new BasicAudioPlaylist(
                title,
                artist,
                artwork,
                TidalConstants.ALBUM_URL + id,
                "album",
                tracks,
                null,
                false
        );
    }

    private AudioItem loadPlaylist(String id) {
        JsonBrowser document = requestApi(playlistUri(id));
        JsonBrowser playlist = firstDataItem(document);

        if (playlist.isNull()) {
            return AudioReference.NO_TRACK;
        }

        JsonBrowser attributes = playlist.get("attributes");

        String title = firstNonBlank(
                attributes.get("title").text(),
                attributes.get("name").text(),
                "TIDAL playlist"
        );

        String creator = firstNonBlank(
                attributes.get("creatorName").text(),
                attributes.get("ownerName").text(),
                "TIDAL"
        );

        String artwork = firstArtwork(document);

        List<AudioTrack> tracks = new ArrayList<>(loadIncludedTracks(document));

        String relatedItems = firstNonBlank(
                playlist.get("relationships").get("items").get("links").get("related").text(),
                playlist.get("relationships").get("tracks").get("links").get("related").text()
        );

        if (tracks.isEmpty() && relatedItems != null) {
            tracks.addAll(loadRelationshipTracks(relatedItems));
        }

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new BasicAudioPlaylist(
                title,
                creator,
                artwork,
                TidalConstants.PLAYLIST_URL + id,
                "playlist",
                tracks,
                null,
                false
        );
    }

    private AudioItem loadArtistTopTracks(String id) {
        JsonBrowser document = requestApi(artistTopTracksUri(id));
        JsonBrowser data = document.get("data");

        if (data.index(0).isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();

        for (JsonBrowser item : data.values()) {
            AudioTrack track = buildTrackFromReferenceOrResource(item, document);

            if (track != null) {
                tracks.add(track);
            }
        }

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new BasicAudioPlaylist(
                "TIDAL artist tracks",
                "TIDAL",
                null,
                TidalConstants.ARTIST_URL + id,
                "artist",
                tracks,
                null,
                false
        );
    }

    private List<AudioTrack> loadRelationshipTracks(String relatedUrl) {
        List<AudioTrack> tracks = new ArrayList<>();

        if (relatedUrl == null || relatedUrl.isBlank()) {
            return tracks;
        }

        String next = relatedUrl;

        while (next != null && !next.isBlank()) {
            JsonBrowser page = requestApi(resolveApiUri(next));
            JsonBrowser data = page.get("data");

            for (JsonBrowser item : data.values()) {
                AudioTrack track = buildTrackFromReferenceOrResource(item, page);

                if (track != null) {
                    tracks.add(track);
                }
            }

            next = page.get("links").get("next").text();
        }

        return tracks;
    }

    private List<AudioTrack> loadIncludedTracks(JsonBrowser document) {
        List<AudioTrack> tracks = new ArrayList<>();

        for (JsonBrowser included : document.get("included").values()) {
            String type = included.get("type").text();

            if ("tracks".equals(type) || "track".equals(type)) {
                AudioTrack track = buildTrack(included, document);

                if (track != null) {
                    tracks.add(track);
                }
            }
        }

        return tracks;
    }

    private AudioTrack buildTrackFromReferenceOrResource(JsonBrowser item, JsonBrowser document) {
        if (!item.get("attributes").isNull()) {
            return buildTrack(item, document);
        }

        String id = firstNonBlank(
                item.get("id").text(),
                item.get("data").get("id").text()
        );

        if (id == null || id.isBlank()) {
            return null;
        }

        AudioItem audioItem = loadTrack(id);

        if (audioItem instanceof AudioTrack) {
            return (AudioTrack) audioItem;
        }

        return null;
    }

    private AudioTrack buildTrack(JsonBrowser trackResource, JsonBrowser document) {
        if (trackResource == null || trackResource.isNull()) {
            return null;
        }

        JsonBrowser attributes = trackResource.get("attributes");

        String id = trackResource.get("id").text();

        if (id == null || id.isBlank()) {
            return null;
        }

        String title = firstNonBlank(
                attributes.get("title").text(),
                attributes.get("name").text(),
                "Unknown title"
        );

        String author = firstNonBlank(
                firstRelationshipArtistName(trackResource, document),
                firstIncludedAttribute(document, "artists", "name"),
                attributes.get("artistName").text(),
                attributes.get("artists").index(0).get("name").text()
        );

        long duration = parseDurationMillis(attributes);

        String isrc = firstNonBlank(
                attributes.get("isrc").text(),
                attributes.get("ISRC").text()
        );

        boolean explicit = parseBoolean(
                attributes.get("explicit").text(),
                attributes.get("explicitContent").text(),
                attributes.get("contentRating").text()
        );

        String artwork = firstNonBlank(
                firstRelationshipArtwork(trackResource, document),
                firstArtwork(document),
                attributes.get("image").text(),
                attributes.get("cover").text()
        );

        AudioTrackInfo info = new AudioTrackInfo(
                title,
                author,
                duration,
                id,
                false,
                TidalConstants.TRACK_URL + id,
                artwork,
                explicit,
                isrc
        );

        if (isrc != null && !isrc.isBlank()) {
            isrcCache.put(id, isrc);
        }

        return new ThirdPartyAudioTrack(info, this);
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

        return document.get("data");
    }

    private static URI trackUri(String id) {
        try {
            return buildUri(TidalConstants.TRACK_API_URL)
                    .addParameter("filter[id]", id)
                    .addParameter("countryCode", DEFAULT_COUNTRY_CODE)
                    .addParameter("locale", DEFAULT_LOCALE)
                    .addParameter("include", "artists,albums,coverArt")
                    .build();
        } catch (URISyntaxException e)  {
            return null;
        }
    }

    private static URI albumUri(String id) {
        try {
            return buildUri(TidalConstants.ALBUM_API_URL)
                    .addParameter("filter[id]", id)
                    .addParameter("countryCode", DEFAULT_COUNTRY_CODE)
                    .addParameter("locale", DEFAULT_LOCALE)
                    .addParameter("include", "artists,items,tracks,coverArt")
                    .build();
        } catch (URISyntaxException e)  {
            return null;
        }

    }

    private static URI playlistUri(String id) {
        try {
            return buildUri(TidalConstants.PLAYLIST_API_URL)
                    .addParameter("filter[id]", id)
                    .addParameter("countryCode", DEFAULT_COUNTRY_CODE)
                    .addParameter("locale", DEFAULT_LOCALE)
                    .addParameter("include", "items,tracks,coverArt")
                    .build();
        } catch (URISyntaxException e)  {
            return null;
        }

    }

    private static URI searchTracksUri(String query) {
        try {
            return buildUri(String.format(TidalConstants.SEARCH_API_URL, encodePathSegment(query)))
                    .addParameter("countryCode", DEFAULT_COUNTRY_CODE)
                    .addParameter("locale", DEFAULT_LOCALE)
                    .addParameter("include", "tracks,artists,albums,coverArt")
                    .build();
        } catch (URISyntaxException e)  {
            return null;
        }

    }

    private static URI artistTopTracksUri(String artistId) {
        try {
            return buildUri(String.format(TidalConstants.ARTIST_TOP_TRACKS, artistId))
                    .addParameter("countryCode", DEFAULT_COUNTRY_CODE)
                    .addParameter("locale", DEFAULT_LOCALE)
                    .addParameter("include", "tracks,artists,albums,coverArt")
                    .build();
        } catch (URISyntaxException e)  {
            return null;
        }

    }

    private static URI resolveApiUri(String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return URI.create(value);
        }

        if (value.startsWith("/")) {
            return URI.create(TidalConstants.API_URL + value);
        }

        return URI.create(TidalConstants.API_URL + "/" + value);
    }

    private static SafeUriBuilder buildUri(String base) {
        return new SafeUriBuilder(base);
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String firstRelationshipArtistName(JsonBrowser track, JsonBrowser document) {
        JsonBrowser artists = track.get("relationships").get("artists").get("data");

        for (JsonBrowser artistRef : artists.values()) {
            String id = artistRef.get("id").text();
            String name = findIncludedAttributeById(document, "artists", id, "name");

            if (name != null) {
                return name;
            }
        }

        return null;
    }

    private static String firstRelationshipArtwork(JsonBrowser track, JsonBrowser document) {
        JsonBrowser artwork = track.get("relationships").get("coverArt").get("data");

        String id = artwork.get("id").text();

        if (id != null && !id.isBlank()) {
            return findIncludedArtworkById(document, id);
        }

        return null;
    }

    private static String firstIncludedAttribute(JsonBrowser document, String type, String attributeName) {
        for (JsonBrowser included : document.get("included").values()) {
            if (type.equals(included.get("type").text())) {
                String value = included.get("attributes").get(attributeName).text();

                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }

        return null;
    }

    private static String findIncludedAttributeById(JsonBrowser document, String type, String id, String attributeName) {
        if (id == null || id.isBlank()) {
            return null;
        }

        for (JsonBrowser included : document.get("included").values()) {
            if (type.equals(included.get("type").text()) && id.equals(included.get("id").text())) {
                String value = included.get("attributes").get(attributeName).text();

                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }

        return null;
    }

    private static String firstArtwork(JsonBrowser document) {
        for (JsonBrowser included : document.get("included").values()) {
            String type = included.get("type").text();

            if ("coverArt".equals(type) || "artworks".equals(type) || "artwork".equals(type)) {
                String url = artworkFromResource(included);

                if (url != null) {
                    return url;
                }
            }
        }

        return null;
    }

    private static String findIncludedArtworkById(JsonBrowser document, String id) {
        for (JsonBrowser included : document.get("included").values()) {
            String type = included.get("type").text();

            if (id.equals(included.get("id").text())
                    && ("coverArt".equals(type) || "artworks".equals(type) || "artwork".equals(type))) {
                return artworkFromResource(included);
            }
        }

        return null;
    }

    private static String artworkFromResource(JsonBrowser resource) {
        JsonBrowser attributes = resource.get("attributes");

        String url = firstNonBlank(
                attributes.get("url").text(),
                attributes.get("href").text(),
                attributes.get("imageUrl").text()
        );

        if (url == null) {
            return null;
        }

        return url
                .replace("{w}", "800")
                .replace("{h}", "800")
                .replace("{width}", "800")
                .replace("{height}", "800");
    }

    private static long parseDurationMillis(JsonBrowser attributes) {
        String millis = attributes.get("durationInMillis").text();

        if (millis != null && !millis.isBlank()) {
            try {
                return Long.parseLong(millis);
            } catch (NumberFormatException ignored) {
                // Try seconds below.
            }
        }

        String seconds = attributes.get("duration").text();

        if (seconds != null && !seconds.isBlank()) {
            try {
                return (long) (Double.parseDouble(seconds) * 1000.0);
            } catch (NumberFormatException ignored) {
                // Unknown duration.
            }
        }

        return Long.MAX_VALUE;
    }

    private static boolean parseBoolean(String... values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }

            if ("true".equalsIgnoreCase(value) || "explicit".equalsIgnoreCase(value)) {
                return true;
            }
        }

        return false;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private static class SafeUriBuilder extends URIBuilder {
        private SafeUriBuilder(String string) {
            super(URI.create(string));
        }

        @Override
        public URI build() {
            try {
                return super.build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}