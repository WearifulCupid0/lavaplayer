package com.sedmelluq.lavaplayer.extensions.thirdpartysources.deezer;

import com.sedmelluq.lavaplayer.extensions.thirdpartysources.*;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
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

import static com.sedmelluq.lavaplayer.extensions.thirdpartysources.deezer.DeezerConstants.*;
import static com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class DeezerAudioSourceManager extends ThirdPartyAudioSourceManager implements HttpConfigurable {
    private static final String DEEZER_URL_REGEX = "^(?:https://|http://|)(?:www\\.|)deezer\\.com/(?:[a-zA-Z]{2}/)(track|album|playlist|artist)/(\\d+)";
	
    private static final Pattern deezerUrlPattern = Pattern.compile(DEEZER_URL_REGEX);

    private static final String SEARCH_PREFIX = "dzsearch:";

    private final Map<String, String> isrcCache = new HashMap<>();
    private final boolean allowSearch;
    private final HttpInterfaceManager httpInterfaceManager;

    public DeezerAudioSourceManager(AudioPlayerManager playerManager) {
        this(true, true, playerManager);
    }

    public DeezerAudioSourceManager(boolean allowSearch, boolean fetchIsrc, AudioPlayerManager playerManager) {
        super(playerManager, fetchIsrc);
        this.allowSearch = allowSearch;
        
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "deezer";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference) {
        if (reference.identifier.startsWith(SEARCH_PREFIX) && allowSearch) {
            return this.loadSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
        }
        
        Matcher matcher = deezerUrlPattern.matcher(reference.identifier);
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

    private AudioItem loadTrack(String id) {
        JsonBrowser track = this.requestApi(TRACK_API_URL + id);
        if(track.isNull()) {
            return AudioReference.NO_TRACK;
        }
        return buildTrack(track, track.get("album"));
    }

    private AudioItem loadArtist(String id) {
        try {
            URI uri = new URIBuilder(String.format(ARTIST_API_URL, id))
            .addParameter("limit", "100").build();

            JsonBrowser json = this.requestApi(uri);
            if(json.isNull() || json.get("data").index(0).isNull()) {
                return AudioReference.NO_TRACK;
            }
        
            List<AudioTrack> tracks = new ArrayList<>();
            for(JsonBrowser track : json.get("data").values()) {
                tracks.add(buildTrack(track, track.get("album")));
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
            throw new FriendlyException("Failed to load Deezer search result", SUSPICIOUS, e);
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
        JsonBrowser album = this.requestApi(ALBUM_API_URL + id);
        if(album.isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for(JsonBrowser track : album.get("tracks").get("data").values()) {
            tracks.add(buildTrack(track, album));
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
        JsonBrowser playlist = this.requestApi(PLAYLIST_API_URL + id);
        if(playlist.isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for(JsonBrowser track : playlist.get("tracks").get("data").values()) {
            tracks.add(buildTrack(track, track.get("album")));
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
            URI uri = new URIBuilder(SEARCH_API_URL)
            .addParameter("limit", "100")
            .addParameter("q", query).build();

            JsonBrowser json = this.requestApi(uri);
            if(json.get("data").index(0).isNull()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();
            for(JsonBrowser track : json.get("data").values()) {
                tracks.add(buildTrack(track, track.get("album")));
            }

            return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Deezer search result", SUSPICIOUS, e);
        }
    }

    private AudioTrack buildTrack(JsonBrowser trackInfo, JsonBrowser albumInfo) {
        String identifier = trackInfo.get("id").text();
        AudioTrackInfo info = new AudioTrackInfo(
            trackInfo.get("title").safeText(),
            trackInfo.get("artist").get("name").safeText(),
            (long) (trackInfo.get("duration").as(Double.class) * 1000.0),
            identifier,
            false,
            trackInfo.get("link").text(),
            albumInfo.get("cover_xl").text()
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
                throw new IOException("Deezer api request failed with status code: " + statusCode);
            }

            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            return JsonBrowser.parse(responseText);
        } catch (IOException e) {
            throw new FriendlyException("Failed to make a request to Deezer Api", SUSPICIOUS, e);
        }
    }
}
