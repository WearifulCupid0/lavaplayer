package com.sedmelluq.discord.lavaplayer.source.deezer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeezerAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String DEEZER_URL_REGEX = "^(?:https://|http://|)(?:www\\.|)deezer\\.com/(?:[a-zA-Z]{2}/|)(track|album|playlist|artist)/(\\d+)";
    private static final String SHARE_URL = "https://deezer.page.link/";
    private static final Pattern deezerUrlPattern = Pattern.compile(DEEZER_URL_REGEX);
    private static final String SEARCH_PREFIX = "dzsearch:";
    private static final String ISRC_PREFIX = "dzisrc:";
    private final HttpInterfaceManager httpInterfaceManager;
    private final boolean allowSearch;
    private String licenseToken;
    private String sessionId;
    private String apiToken;

    public DeezerAudioSourceManager() {
        this(true);
    }
    public DeezerAudioSourceManager(boolean allowSearch) {
        this.allowSearch = allowSearch;
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
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

        if (identifier.startsWith(SHARE_URL)) {
            HttpGet request = new HttpGet(identifier);
            request.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());
            try (CloseableHttpResponse response = getHttpInterface().execute(request)) {
                if (response.getStatusLine().getStatusCode() == 302) {
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

    public HttpInterface getHttpInterface() {
        return this.httpInterfaceManager.getInterface();
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
        AudioTrackInfo info = new AudioTrackInfo(
                trackInfo.get("title").safeText(),
                trackInfo.get("artist").get("name").safeText(),
                trackInfo.get("duration").asLong(0) * 1000,
                trackInfo.get("id").text(),
                false,
                trackInfo.get("link").text(),
                trackInfo.get("album").get("cover_xl").text(),
                trackInfo.get("explicit_lyrics").asBoolean(false)
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

    private void getCredentials() throws IOException {
        HttpPost post = new HttpPost(DeezerConstants.AJAX_URL + "?method=deezer.getUserData&api_token=&input=3&api_version=1.0&cid=550330597");
        try (CloseableHttpResponse response = this.getHttpInterface().execute(post)) {
            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            this.sessionId = json.get("results").get("SESSION_ID").text();
            this.apiToken = json.get("results").get("checkForm").text();
            this.licenseToken = json.get("results").get("USER").get("OPTIONS").get("license_token").text();
            if (this.sessionId == null || this.apiToken == null) throw  new IOException("Failed to fetch new credentials");
        }
    }

    public URI getMediaURL(String songId) throws Exception {
        if (this.licenseToken == null) this.getCredentials();
        HttpPost postMediaURL = new HttpPost(DeezerConstants.MEDIA_URL);
        String token = this.getTrackToken(songId, false);
        if (token == null) throw  new Exception("Song unavailable");
        postMediaURL.setEntity(new StringEntity(String.format(DeezerConstants.MEDIA_PAYLOAD, this.licenseToken, token), ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse response = this.getHttpInterface().execute(postMediaURL)) {
            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            JsonBrowser error = json.get("data").index(0).get("errors").index(0);
            if (error.get("code").asLong(0) != 0) {
                throw new FriendlyException("Error while loading track: " + error.get("message").text(), FriendlyException.Severity.COMMON, null);
            }
            return new URI(json.get("data").index(0).get("media").index(0).get("sources").index(0).get("url").text());
        }
    }

    private String getTrackToken(String songId, boolean secondTime) throws IOException {
        if (this.apiToken == null || this.sessionId == null) this.getCredentials();
        HttpPost postSongData = new HttpPost(DeezerConstants.AJAX_URL + "?method=song.getData&input=3&api_version=1.0&cid=550330597&api_token=" + this.apiToken);
        postSongData.setEntity(new StringEntity("{\"SNG_ID\":\"" + songId + "\"}", ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse response = this.getHttpInterface().execute(postSongData)) {
            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            if (this.isTokenInvalid(json) && !secondTime) {
                this.getCredentials();
                return this.getTrackToken(songId, true);
            } else if (this.isTokenInvalid(json) && secondTime) {
                throw new IOException("Failed to load new deezer api token.");
            }
            return json.get("results").get("TRACK_TOKEN").text();
        }
    }

    private boolean isTokenInvalid(JsonBrowser response) {
        if (response.get("error").isList()) return false;
        return !response.get("error").get("VALID_TOKEN_REQUIRED").isNull();
    }

}
