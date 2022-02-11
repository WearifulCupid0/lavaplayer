package com.sedmelluq.lavaplayer.extensions.thirdpartysources.tidal;

import com.sedmelluq.lavaplayer.extensions.thirdpartysources.*;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.ThreadLocalHttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import static com.sedmelluq.lavaplayer.extensions.thirdpartysources.tidal.TidalConstants.*;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class TidalAudioSourceManager extends ThirdPartyAudioSourceManager implements HttpConfigurable {
    private static final String TIDAL_URL_REGEX = "^(?:https://|http://|)(?:www\\.|listen\\.|)tidal\\.com/(?:browse/|)(track|playlist|album|mix)/([a-zA-Z0-9-_]+)";
	
    private static final Pattern tidalUrlPattern = Pattern.compile(TIDAL_URL_REGEX);

    private static final String SEARCH_PREFIX = "tdsearch:";

    private final Map<String, String> isrcCache = new HashMap<>();
    private final boolean allowSearch;
    private final TidalTokenTracker tokenTracker;
    private final HttpInterfaceManager httpInterfaceManager;

    public TidalAudioSourceManager(AudioPlayerManager playerManager) {
        this(true, true, playerManager);
    }

    public TidalAudioSourceManager(boolean allowSearch, boolean fetchIsrc, AudioPlayerManager playerManager) {
        super(playerManager, fetchIsrc);
        this.allowSearch = allowSearch;
        
        this.httpInterfaceManager = new ThreadLocalHttpInterfaceManager(
            HttpClientTools.createSharedCookiesHttpBuilder(),
            RequestConfig.custom()
            .setConnectTimeout(10000)
            .build()
        );
        this.tokenTracker = new TidalTokenTracker(httpInterfaceManager);
        this.httpInterfaceManager.setHttpContextFilter(new TidalHttpContextFilter(this.tokenTracker));
    }

    @Override
    public String getSourceName() {
        return "tidal";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference) {
        if (reference.identifier.startsWith(SEARCH_PREFIX) && allowSearch) {
            return this.loadSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
        }

        Matcher matcher = tidalUrlPattern.matcher(reference.identifier);
        if (matcher.find()) {
            String id = matcher.group(2);
            switch (matcher.group(1)) {
                case "album": return this.loadAlbum(id);
                case "track": return this.loadTrack(id);
                case "playlist": return this.loadPlaylist(id);
                case "mix": return this.loadMix(id);
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
    public void shutdown() {
        //nothing to shutdown
    }
    
    public String fetchIsrc(AudioTrack track) {
        if (track.getInfo().uri == null) return null;
        
        String identifier = track.getIdentifier();
        if (this.isrcCache.containsKey(identifier)) return this.isrcCache.get(identifier);
        
        JsonBrowser json = this.requestApi(TRACK_API_URL + identifier);
        String isrc = json.get("isrc").text();
        if (isrc != null) {
            if (!this.isrcCache.containsKey(identifier)) {
                this.isrcCache.put(identifier, isrc);
            }
            return isrc;
        }
        
        return null;
    }

    /**
     * @return Get an HTTP interface for a playing track.
     */
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
        return this.tokenTracker;
    }

    private AudioItem loadSearch(String query) {
        try {
            URI uri = new URIBuilder(SEARCH_API_URL)
            .addParameter("query", query).build();

            JsonBrowser json = this.requestApi(uri);
            if(json.get("items").index(0).isNull()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();
            for(JsonBrowser track : json.get("items").values()) {
                tracks.add(buildTrack(track, null));
            }

            return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
        } catch (Exception e) {
            throw new FriendlyException("Failed to load TIDAL search result", SUSPICIOUS, e);
        }
    }

    private AudioItem loadMix(String id) {
        try {
            URI uri = new URIBuilder(MIX_API_URL)
            .addParameter("mixId", id)
            .addParameter("deviceType", "BROWSER").build();

            JsonBrowser json = this.requestApi(uri);
            if(json.get("rows").index(0).isNull()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();
            List<JsonBrowser> items = json.get("rows").index(1).get("modules").index(0).get("pagedList").get("items").values();
            for(JsonBrowser track : items) {
                tracks.add(buildTrack(track, null));
            }
            JsonBrowser mix = json.get("rows").index(0).get("modules").index(0).get("mix");

            return new BasicAudioPlaylist(
                mix.get("subTitle").safeText(),
                mix.get("title").text(),
                mix.get("images").get("LARGE").get("url").text(),
                MIX_URL + id,
                "mix",
                tracks,
                null,
                false
            );
        } catch (Exception e) {
            throw new FriendlyException("Failed to load TIDAL search result", SUSPICIOUS, e);
        }
    }

    private AudioItem loadTrack(String id) {
        JsonBrowser json = this.requestApi(TRACK_API_URL + id);
        if (json.isNull()) {
            return AudioReference.NO_TRACK;
        }
        return buildTrack(json, json.get("album").get("cover").text());
    }

    private AudioItem loadAlbum(String id) {
        JsonBrowser album = this.requestApi(ALBUM_API_URL + id);
        if (album.isNull()) {
            return AudioReference.NO_TRACK;
        }
        Double total = album.get("numberOfVideos").as(Double.class) + album.get("numberOfTracks").as(Double.class);
        List<AudioTrack> tracks = new ArrayList<>();
        for(int i = 0; i < Math.floor(total / 100) + 1; i++) {
            JsonBrowser json = this.requestApi(String.format(ALBUM_TRACK_API_URL, id) + "?offset=" + i);
            for(JsonBrowser track : json.get("items").values()) {
                tracks.add(buildTrack(track, null));
            }
        }
        return new BasicAudioPlaylist(
            album.get("title").safeText(),
            album.get("artist").get("name").safeText(),
            !album.get("cover").isNull()
            ? String.format(CDN_URL, album.get("cover").text().replaceAll("-", "/"))
            : null,
            ALBUM_URL + album.get("id").text(),
            album.get("type").text().toLowerCase(),
            tracks,
            null,
            false
        );
    }

    private AudioItem loadPlaylist(String id) {
        JsonBrowser playlist = this.requestApi(PLAYLIST_API_URL + id);
        if (playlist.isNull()) {
            return AudioReference.NO_TRACK;
        }
        Double total = playlist.get("numberOfVideos").as(Double.class) + playlist.get("numberOfTracks").as(Double.class);
        List<AudioTrack> tracks = new ArrayList<>();
        for(int i = 0; i < Math.floor(total / 100) + 1; i++) {
            JsonBrowser json = this.requestApi(String.format(PLAYLIST_TRACK_API_URL, id) + "?offset=" + i);
            for(JsonBrowser track : json.get("items").values()) {
                tracks.add(buildTrack(track, null));
            }
        }
        return new BasicAudioPlaylist(
            playlist.get("title").safeText(),
            playlist.get("creator").get("name").isNull() ? "TIDAL" : playlist.get("creator").get("name").safeText(),
            !playlist.get("image").isNull()
            ? String.format(CDN_URL, playlist.get("image").text().replaceAll("-", "/"))
            : null,
            PLAYLIST_URL + playlist.get("uuid").text(),
            "playlist",
            tracks,
            null,
            false
        );
    }

    private AudioTrack findSelectedTrack(List<AudioTrack> tracks, String trackId) {
        if (trackId != null) {
            for (AudioTrack track : tracks) {
                if (trackId.equals(track.getIdentifier())) {
                    return track;
                }
            }
        }
    
        return null;
    }

    private AudioTrack buildTrack(JsonBrowser trackInfo, String imageUrl) {
        String identifier = trackInfo.get("id").text();
        String artworkId = trackInfo.get("album").get("cover").isNull()
        ? trackInfo.get("album").get("videoCover").text()
        : trackInfo.get("album").get("cover").text();
        JsonBrowser artist = trackInfo.get("artist").isNull()
        ? trackInfo.get("artists").index(0)
        : trackInfo.get("artist");
        AudioTrackInfo info = new AudioTrackInfo(
            trackInfo.get("title").safeText(),
            artist.get("name").safeText(),
            (long) (trackInfo.get("duration").as(Double.class) * 1000.0),
            identifier,
            false,
            TRACK_URL + identifier,
            artworkId != null ? String.format(CDN_URL, artworkId.replaceAll("-", "/")) : imageUrl != null ? imageUrl : null
        );

        String isrc = trackInfo.get("isrc").text();
        if (isrc != null && !this.isrcCache.containsKey(identifier)) {
            this.isrcCache.put(identifier, isrc);
        }

        return new ThirdPartyAudioTrack(info, isrc, this);
    }

    private JsonBrowser requestApi(String uri) {
        return this.requestApi(URI.create(uri));
    }

    private JsonBrowser requestApi(URI uri) {
        try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(uri))) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("TIDAL api request failed with status code: " + statusCode);
            }

            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            return JsonBrowser.parse(responseText);
        } catch (IOException e) {
            throw new FriendlyException("Failed to make a request to TIDAL Api", SUSPICIOUS, e);
        }
    }
}
