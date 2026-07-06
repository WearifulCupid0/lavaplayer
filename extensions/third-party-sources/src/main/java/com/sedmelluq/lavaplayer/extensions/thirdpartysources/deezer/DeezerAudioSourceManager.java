package com.sedmelluq.lavaplayer.extensions.thirdpartysources.deezer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.io.*;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.SourceTools;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.ThirdPartyAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeezerAudioSourceManager extends ThirdPartyAudioSourceManager implements HttpConfigurable {
    private static final Logger log = LoggerFactory.getLogger(DeezerAudioSourceManager.class);

    private static final String DEEZER_URL_REGEX = "^(?:https://|http://|)(?:www\\.|)deezer\\.com/(?:[a-zA-Z]{2}/|)(track|album|playlist|artist)/(\\d+)";
    private static final String SHARE_URL = "https://deezer.page.link/";
    private static final String NEW_SHARE_URL = "https://link.deezer.com/";
    private static final Pattern deezerUrlPattern = Pattern.compile(DEEZER_URL_REGEX);
    private static final String SEARCH_PREFIX = "dzsearch:";
    private static final String ISRC_PREFIX = "dzisrc:";
    private final HttpInterfaceManager httpInterfaceManager;
    private final boolean allowSearch;

    private final CookieStore deezerCookieStore = new BasicCookieStore();
    private final Object credentialLock = new Object();

    private String licenseToken;
    private String sessionId;
    private String apiToken;
    private String uniqueId;

    public final String masterKey;
    public final String deezerArl;

    public DeezerAudioSourceManager(AudioPlayerManager playerManager) { this(playerManager, null, null, true, true); }
    public DeezerAudioSourceManager(AudioPlayerManager playerManager, boolean allowSearch) { this(playerManager, null, null, allowSearch, true); }
    public DeezerAudioSourceManager(AudioPlayerManager playerManager, String masterKey, String deezerArl) {
        this(playerManager, masterKey, deezerArl, true, true);
    }
    public DeezerAudioSourceManager(AudioPlayerManager playerManager, String masterKey, String deezerArl, boolean allowSearch, boolean fetchIsrc) {
        super(playerManager, fetchIsrc);

        this.deezerArl = SourceTools.firstNonBlank(deezerArl, SourceTools.getPropertyOrEnv("DEEZER_ARL"));
        this.masterKey = SourceTools.firstNonBlank(masterKey, SourceTools.getPropertyOrEnv("DEEZER_MASTER_KEY"));
        this.allowSearch = allowSearch;
        this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
    }

    public boolean canPlayNative() {
        return !SourceTools.isBlank(this.masterKey) && !SourceTools.isBlank(this.deezerArl);
    }

    @Override
    public String getSourceName() {
        return "deezer";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference) {
        String identifier = reference.identifier;

        if (identifier.startsWith(SEARCH_PREFIX) && allowSearch) {
            return this.loadSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
        }

        if (identifier.startsWith(ISRC_PREFIX)) {
            return this.loadTrackISRC(reference.identifier.substring(ISRC_PREFIX.length()).trim());
        }

        if (identifier.startsWith(NEW_SHARE_URL)) {
            HttpGet request = new HttpGet(identifier);
            request.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());
            try (CloseableHttpResponse response = getHttpInterface().execute(request)) {
                if (response.getStatusLine().getStatusCode() == 302 || response.getStatusLine().getStatusCode() == 301) {
                    String location = response.getFirstHeader("Location").getValue();
                    URI uri = URI.create(location);
                    String query = uri.getRawQuery();
                    if (query != null && !query.isBlank()) {
                        for (String part : query.split("&")) {
                            int separator = part.indexOf('=');
                            if (separator > 0) {
                                String key = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8);
                                String value = URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);

                                if (key.equals("awf") && value.startsWith("https://www.deezer.com/")) {
                                    return this.loadItem(playerManager, new AudioReference(value, reference.title));
                                }
                            }
                        }
                    }
                }
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (identifier.startsWith(SHARE_URL)) {
            HttpGet request = new HttpGet(identifier);
            request.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());
            try (CloseableHttpResponse response = getHttpInterface().execute(request)) {
                if (response.getStatusLine().getStatusCode() == 302 || response.getStatusLine().getStatusCode() == 301) {
                    String location = response.getFirstHeader("Location").getValue();
                    if (location.startsWith("https://www.deezer.com/")) {
                        return this.loadItem(playerManager, new AudioReference(location, reference.title));
                    }
                }
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Matcher matcher = deezerUrlPattern.matcher(identifier);
        if (matcher.find()) {
            String id = matcher.group(2);
            switch (matcher.group(1)) {
                case "album": return this.loadAlbum(id);
                case "track": return this.loadTrack(id);
                case "playlist": return this.loadPlaylist(id);
                case "artist": return this.loadArtist(id);
                default: return null;
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
        return new DeezerAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager);
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
    }

    public HttpInterface getHttpInterface() { return getHttpInterface(false); }

    public HttpInterface getHttpInterface(boolean ajaxApi) {
        HttpInterface httpInterface = httpInterfaceManager.getInterface();

        if (ajaxApi) {
            httpInterface.getContext().setCookieStore(deezerCookieStore);

            RequestConfig currentConfig = httpInterface.getContext().getRequestConfig();

            RequestConfig requestConfig = RequestConfig.copy(
                            currentConfig != null ? currentConfig : RequestConfig.DEFAULT
                    )
                    .setCookieSpec(CookieSpecs.STANDARD)
                    .build();

            httpInterface.getContext().setRequestConfig(requestConfig);
        }

        return httpInterface;
    }

    private void putDeezerCookie(String name, String value) {
        if (SourceTools.isBlank(value)) {
            return;
        }

        List<Cookie> cookies = new ArrayList<>(deezerCookieStore.getCookies());

        deezerCookieStore.clear();

        for (Cookie existing : cookies) {
            if (!existing.getName().equals(name)) {
                deezerCookieStore.addCookie(existing);
            }
        }

        BasicClientCookie cookie = new BasicClientCookie(name, value);

        cookie.setDomain("deezer.com");
        cookie.setPath("/");
        cookie.setSecure(true);

        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, ".deezer.com");
        cookie.setAttribute(ClientCookie.PATH_ATTR, "/");

        deezerCookieStore.addCookie(cookie);
    }

    private void ensureArlCookie() {
        putDeezerCookie("arl", deezerArl);
    }

    private String buildCookieHeader() {
        StringBuilder builder = new StringBuilder();

        for (Cookie cookie : deezerCookieStore.getCookies()) {
            if (builder.length() > 0) {
                builder.append("; ");
            }

            builder.append(cookie.getName())
                    .append("=")
                    .append(cookie.getValue());
        }

        return builder.toString();
    }

    private void applyDeezerHeaders(HttpUriRequest request) {
        request.setHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        request.setHeader("Accept", "application/json, text/plain, */*");
        request.setHeader("Accept-Language", "en-US,en;q=0.9,pt-BR;q=0.8,pt;q=0.7");
        request.setHeader("Origin", "https://www.deezer.com");
        request.setHeader("Referer", "https://www.deezer.com/");
        request.setHeader("X-Requested-With", "XMLHttpRequest");

        String cookieHeader = buildCookieHeader();

        if (!SourceTools.isBlank(cookieHeader)) {
            request.setHeader("Cookie", cookieHeader);
        }
    }

    private AudioItem loadTrackISRC(String isrc) {
        return this.loadTrack("isrc:" + isrc);
    }

    private AudioItem loadTrack(String id) {
        JsonBrowser track = this.requestApi(DeezerConstants.API_URL + "/track/" + id);
        if(track == null || track.get("id").isNull()) {
            return AudioReference.NO_TRACK;
        }
        return buildTrack(track);
    }

    private AudioItem loadArtist(String id) {
        try {
            URI uri = new URIBuilder(DeezerConstants.API_URL + "/artist/" + id + "/top")
                    .addParameter("limit", "100").build();

            JsonBrowser json = this.requestApi(uri);
            if(json == null || json.isNull() || json.get("data").index(0).isNull()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();
            for(JsonBrowser track : json.get("data").values()) {
                tracks.add(buildTrack(track));
            }
            JsonBrowser artist = findArtist(json.get("data").index(0).get("contributors").values(), id);
            return new BasicAudioPlaylist(
                    artist.get("name").safeText(),
                    artist.get("name").safeText(),
                    artist.get("picture_xl").text(),
                    artist.get("link").text(),
                    "artist",
                    tracks,
                    null,
                    false
            );
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Deezer search result", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private JsonBrowser findArtist(List<JsonBrowser> artists, String id) {
        for (JsonBrowser artist : artists) {
            if (id.equals(artist.get("id").text())) {
                return artist;
            }
        }
        return null;
    }

    private AudioItem loadAlbum(String id) {
        JsonBrowser album = this.requestApi(DeezerConstants.API_URL + "/album/" + id);
        if(album == null || album.get("id").isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for(JsonBrowser track : album.get("tracks").get("data").values()) {
            tracks.add(buildTrack(track));
        }
        return new BasicAudioPlaylist(
                album.get("title").text(),
                album.get("artist").get("name").text(),
                album.get("cover_xl").text(),
                album.get("link").text(),
                album.get("record_type").safeText().toLowerCase(),
                tracks,
                null,
                false
        );
    }

    private AudioItem loadPlaylist(String id) {
        JsonBrowser playlist = this.requestApi(DeezerConstants.API_URL + "/playlist/" + id);
        if(playlist == null || playlist.get("id").isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for(JsonBrowser track : playlist.get("tracks").get("data").values()) {
            tracks.add(buildTrack(track));
        }
        return new BasicAudioPlaylist(
                playlist.get("title").text(),
                playlist.get("creator").get("name").text(),
                playlist.get("picture_xl").text(),
                playlist.get("link").text(),
                "playlist",
                tracks,
                null,
                false
        );
    }

    private AudioItem loadSearch(String query) {
        try {
            URI uri = new URIBuilder(DeezerConstants.API_URL + "/search")
                    .addParameter("limit", "100")
                    .addParameter("q", query).build();

            JsonBrowser json = this.requestApi(uri);
            if(json == null || json.get("data").values().isEmpty()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();
            for(JsonBrowser track : json.get("data").values()) {
                tracks.add(buildTrack(track));
            }

            return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Deezer search result", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private AudioTrack buildTrack(JsonBrowser trackInfo) {
        List<AudioTrackAuthorInfo> artists = new ArrayList<>();

        if (!trackInfo.get("contributors").isNull()) {
            for (JsonBrowser artistInfo : trackInfo.get("contributors").values())
                artists.add(new AudioTrackAuthorInfo(artistInfo.get("name").text(), artistInfo.get("link").text()));
        } else {
            JsonBrowser artistInfo = trackInfo.get("artist");
            artists.add(new AudioTrackAuthorInfo(artistInfo.get("name").text(), artistInfo.get("link").text()));
        }

        AudioTrackInfo info = new AudioTrackInfo(
                trackInfo.get("title").safeText(),
                artists,
                trackInfo.get("duration").asLong(0) * 1000,
                trackInfo.get("id").text(),
                false,
                trackInfo.get("link").text(),
                trackInfo.get("album").get("cover_xl").text(),
                trackInfo.get("explicit_lyrics").asBoolean(false),
                trackInfo.get("isrc").text()
        );

        return new DeezerAudioTrack(info, this);
    }

    private JsonBrowser requestApi(String uri) {
        return this.requestApi(URI.create(uri));
    }

    private JsonBrowser requestApi(URI uri) {
        HttpGet get = new HttpGet(uri);
        get.setHeader("Accept", "application/json");
        try (HttpInterface httpInterface = getHttpInterface()) {
            return HttpClientTools.fetchResponseAsJson(httpInterface, get);
        } catch (IOException e) {
            throw new FriendlyException("Failed to make a request to Deezer Api", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    public void setupDeezerHttpInterface(HttpInterface httpInterface) {
        ensureArlCookie();

        httpInterface.getContext().setCookieStore(deezerCookieStore);

        RequestConfig currentConfig = httpInterface.getContext().getRequestConfig();

        RequestConfig requestConfig = RequestConfig.copy(
                        currentConfig != null ? currentConfig : RequestConfig.DEFAULT
                )
                .setCookieSpec(CookieSpecs.STANDARD)
                .build();

        httpInterface.getContext().setRequestConfig(requestConfig);
    }

    private static void checkResponse(JsonBrowser json, String message) {
        if (json == null || json.isNull()) {
            throw new IllegalStateException(message + ": no response");
        }

        String error = json.get("error").safeText();
        if (!error.equals("{}") && !error.equals("[]") && !error.equals("null") && !error.isEmpty()) {
            throw new IllegalStateException(message + ": " + error);
        }

        String errors = json.get("errors").safeText();
        if (!errors.equals("[]") && !errors.equals("null") && !errors.isEmpty()) {
            throw new IllegalStateException(message + ": " + errors);
        }
    }

    private boolean isCredentialsBlank() {
        return isBlankToken(this.licenseToken) ||
                isBlankToken(this.apiToken) ||
                isBlankToken(this.sessionId) ||
                isBlankToken(this.uniqueId);
    }

    private boolean isBlankToken(String value) {
        return SourceTools.isBlank(value) || "null".equalsIgnoreCase(value);
    }

    private boolean isInvalidCsrfResponse(JsonBrowser json) {
        JsonBrowser error = json.get("error");

        if (error.isNull()) {
            return false;
        }

        return !error.get("VALID_TOKEN_REQUIRED").isNull();
    }

    private void clearDeezerSessionCredentials() {
        this.apiToken = null;
        this.licenseToken = null;
        this.sessionId = null;
        this.uniqueId = null;
    }

    private DeezerCredentials getCredentials(HttpInterface httpInterface) throws IOException {
        synchronized (credentialLock) {
            ensureArlCookie();

            HttpGet request = new HttpGet(
                    DeezerConstants.AJAX_URL +
                            "?method=deezer.getUserData&input=3&api_version=1.0&api_token="
            );

            applyDeezerHeaders(request);

            log.debug("Fetching new Deezer credentials...");

            try (CloseableHttpResponse response = httpInterface.execute(request)) {
                captureDeezerCookies(response);

                HttpClientTools.assertSuccessWithContent(response, "deezer credentials");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                checkResponse(json, "Failed to get Deezer user token");

                String apiToken = json.get("results").get("checkForm").text();

                String licenseToken = json.get("results")
                        .get("USER")
                        .get("OPTIONS")
                        .get("license_token")
                        .text();

                this.apiToken = apiToken;
                this.licenseToken = licenseToken;

                captureDeezerCookies(response);

                if (isCredentialsBlank()) {
                    log.debug("Missing Deezer credentials:");
                    log.debug("apiToken blank: {}", SourceTools.isBlank(this.apiToken));
                    log.debug("licenseToken blank: {}", SourceTools.isBlank(this.licenseToken));
                    log.debug("sessionId blank: {}", SourceTools.isBlank(this.sessionId));
                    log.debug("uniqueId blank: {}", SourceTools.isBlank(this.uniqueId));

                    throw new IOException("Failed to fetch new Deezer credentials.");
                }

                log.debug("Deezer api token updated.");

                return new DeezerCredentials(apiToken, licenseToken);
            }
        }
    }

    public DeezerMediaSource getMediaSource(HttpInterface httpInterface, String songId) throws Exception {
        setupDeezerHttpInterface(httpInterface);

        DeezerCredentials credentials;

        if (isCredentialsBlank()) {
            credentials = this.getCredentials(httpInterface);
        } else {
            credentials = new DeezerCredentials(this.apiToken, this.licenseToken);
        }

        DeezerTrackToken token = this.getTrackToken(httpInterface, songId, credentials, false);

        HttpPost request = new HttpPost(DeezerConstants.MEDIA_URL);

        String payload = String.format(DeezerConstants.MEDIA_PAYLOAD, credentials.licenseToken, token.trackToken);

        request.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));

        applyDeezerHeaders(request);

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            captureDeezerCookies(response);

            int statusCode = response.getStatusLine().getStatusCode();

            String body = new String(
                    response.getEntity().getContent().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            if (statusCode == 403) {
                log.debug("Deezer media API 403 body: {}", body);
                log.debug("Deezer media API cookies: {}", buildCookieHeader());
                throw new IOException("Deezer media API returned 403.");
            }

            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("Deezer media API returned " + statusCode + ": " + body);
            }

            JsonBrowser json = JsonBrowser.parse(body);

            checkResponse(json, "Failed to get Deezer media URL");

            log.debug("Deezer media url response: {}", json.format());

            JsonBrowser media = json.get("data")
                    .index(0)
                    .get("media")
                    .index(0);

            if (media.isNull()) {
                throw new IOException("No media found in Deezer response: " + body);
            }

            String format = media.get("format").text();

            String url = media.get("sources")
                    .index(0)
                    .get("url")
                    .text();

            if (SourceTools.isBlank(url)) {
                throw new IOException("Deezer media url is empty.");
            }

            long contentLength = token.trackData
                    .get("FILESIZE_" + format)
                    .asLong(com.sedmelluq.discord.lavaplayer.tools.Units.CONTENT_LENGTH_UNKNOWN);

            return new DeezerMediaSource(
                    new URI(url),
                    contentLength,
                    token.trackId,
                    format
            );
        }
    }

    private DeezerTrackToken getTrackToken(
            HttpInterface httpInterface,
            String songId,
            DeezerCredentials credentials,
            boolean secondTime
    ) throws IOException {
        HttpPost request = new HttpPost(
                DeezerConstants.AJAX_URL +
                        "?method=song.getData&input=3&api_version=1.0&api_token=" +
                        credentials.apiToken
        );

        request.setEntity(new StringEntity(
                "{\"sng_id\":\"" + songId + "\"}",
                ContentType.APPLICATION_JSON
        ));

        applyDeezerHeaders(request);

        log.debug("Fetching Deezer track token with identifier {}", songId);

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            captureDeezerCookies(response);

            HttpClientTools.assertSuccessWithContent(response, "deezer track token");

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

            if (isInvalidCsrfResponse(json)) {
                if (secondTime) {
                    throw new IOException("Failed to get Deezer track token: invalid CSRF token after retry.");
                }

                log.debug("Deezer api token is invalid, refreshing credentials and retrying.");

                clearDeezerSessionCredentials();

                DeezerCredentials newCredentials = getCredentials(httpInterface);

                return getTrackToken(httpInterface, songId, newCredentials, true);
            }

            checkResponse(json, "Failed to get Deezer track token");

            JsonBrowser results = json.get("results");

            String effectiveTrackId = songId;

            JsonBrowser rights = results.get("RIGHTS");

            if ((rights.isNull() || rights.values().isEmpty()) &&
                    !results.get("FALLBACK").get("TRACK_TOKEN").isNull()) {
                results = results.get("FALLBACK");
                effectiveTrackId = results.get("SNG_ID").text();

                log.debug("Track {} has no RIGHTS, using FALLBACK {}", songId, effectiveTrackId);
            }

            String trackToken = results.get("TRACK_TOKEN").text();

            if (isBlankToken(trackToken)) {
                if (secondTime) {
                    throw new IOException("Failed to load new Deezer track token.");
                }

                log.debug("Deezer track token is blank, refreshing credentials and retrying.");

                clearDeezerSessionCredentials();

                DeezerCredentials newCredentials = getCredentials(httpInterface);

                return getTrackToken(httpInterface, songId, newCredentials, true);
            }

            log.debug("Deezer track token for song {} loaded.", songId);

            return new DeezerTrackToken(trackToken, effectiveTrackId, results);
        }
    }

    private void captureDeezerCookies(HttpResponse response) {
        for (Header header : response.getHeaders("Set-Cookie")) {
            String value = header.getValue();

            int equalsIndex = value.indexOf('=');
            if (equalsIndex < 0)
                continue;


            String name = value.substring(0, equalsIndex).trim();

            String cookieValue = value.substring(equalsIndex + 1);
            int separatorIndex = cookieValue.indexOf(';');

            if (separatorIndex >= 0)
                cookieValue = cookieValue.substring(0, separatorIndex);


            if (name.equals("arl") || name.equals("sid") || name.equals("dzr_uniq_id")) {
                putDeezerCookie(name, cookieValue);

                if (name.equals("sid")) {
                    this.sessionId = cookieValue;
                } else if (name.equals("dzr_uniq_id")) {
                    this.uniqueId = cookieValue;
                }
            }
        }

        for (Cookie cookie : deezerCookieStore.getCookies()) {
            if (cookie.getName().equals("sid")) {
                this.sessionId = cookie.getValue();
            } else if (cookie.getName().equals("dzr_uniq_id")) {
                this.uniqueId = cookie.getValue();
            }
        }
    }

    public static final class DeezerMediaSource {
        private final URI url;
        private final long contentLength;
        private final String trackId;
        private final String format;

        public DeezerMediaSource(URI url, long contentLength, String trackId, String format) {
            this.url = url;
            this.contentLength = contentLength;
            this.trackId = trackId;
            this.format = format;
        }

        public URI getUrl() {
            return url;
        }

        public long getContentLength() {
            return contentLength;
        }

        public String getTrackId() {
            return trackId;
        }

        public String getFormat() {
            return format;
        }
    }

    private static final class DeezerCredentials {
        private final String apiToken;
        private final String licenseToken;

        private DeezerCredentials(String apiToken, String licenseToken) {
            this.apiToken = apiToken;
            this.licenseToken = licenseToken;
        }
    }

    private static final class DeezerTrackToken {
        private final String trackToken;
        private final String trackId;
        private final JsonBrowser trackData;

        private DeezerTrackToken(String trackToken, String trackId, JsonBrowser trackData) {
            this.trackToken = trackToken;
            this.trackId = trackId;
            this.trackData = trackData;
        }
    }
}
