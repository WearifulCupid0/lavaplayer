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
import org.apache.http.client.utils.URIBuilder;
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
    private final DeezerHttpContextFilter contextFilter;
    private final boolean allowSearch;

    public DeezerAudioSourceManager() {
        this(true);
    }
    public DeezerAudioSourceManager(boolean allowSearch) {
        this.allowSearch = allowSearch;
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
        contextFilter = new DeezerHttpContextFilter(httpInterfaceManager.getInterface());
        httpInterfaceManager.setHttpContextFilter(contextFilter);
    }

    public String getLicenseToken() {
        return this.contextFilter.getLicenseToken();
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

        if (identifier.startsWith(ISRC_PREFIX) && allowSearch) {
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
        if(track.isNull()) {
            return AudioReference.NO_TRACK;
        }
        return buildTrack(track);
    }

    private AudioItem loadArtist(String id) {
        try {
            URI uri = new URIBuilder(DeezerConstants.API_URL + "/artist/" + id + "/top")
                    .addParameter("limit", "100").build();

            JsonBrowser json = this.requestApi(uri);
            if(json.isNull() || json.get("data").index(0).isNull()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();
            for(JsonBrowser track : json.get("data").values()) {
                tracks.add(buildTrack(track));
            }
            JsonBrowser artist = findArtist(json.get("data").index(0).get("contributors").values(), json.get("data").index(0).get("artist").get("id").text());
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
        if(album.isNull()) {
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
        if(playlist.isNull()) {
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
            if(json.get("data").values().isEmpty()) {
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
        try (CloseableHttpResponse response = getHttpInterface().execute(get)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("Deezer api request failed with status code: " + statusCode);
            }

            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            return JsonBrowser.parse(responseText);
        } catch (IOException e) {
            throw new FriendlyException("Failed to make a request to Deezer Api", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }
}
