package com.sedmelluq.lavaplayer.extensions.thirdpartysources.napster;

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

import static com.sedmelluq.lavaplayer.extensions.thirdpartysources.napster.NapsterConstants.*;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class NapsterAudioSourceManager extends ThirdPartyAudioSourceManager implements HttpConfigurable {
    private static final String NAPSTER_URL_REGEX = "^(?:https://|http://|)(?:www\\.|app\\.|[a-zA-Z]{2}\\.|)napster\\.com/artist/([a-zA-Z0-9-_]+)(?:/album/([a-zA-Z0-9-_]+)(?:/track/([a-zA-Z0-9-_]+)|)|)";
    private static final String NAPSTER_APP_REGEX = "^(?:https://|http://|)(?:www\\.|app\\.|[a-zA-Z]{2}\\.|)napster\\.com/(track|album|playlist)/([a-zA-Z0-9-_\\.]+)";
	
    private static final Pattern napsterUrlPattern = Pattern.compile(NAPSTER_URL_REGEX);
    private static final Pattern napsterAppPattern = Pattern.compile(NAPSTER_APP_REGEX);

    private static final String SEARCH_PREFIX = "npsearch:";

    private final Map<String, String> isrcCache = new HashMap<>();
    private final String apikey;
    private final boolean allowSearch;
    private final HttpInterfaceManager httpInterfaceManager;

    public NapsterAudioSourceManager(AudioPlayerManager playerManager) {
        this(true, true, playerManager);
    }

    public NapsterAudioSourceManager(boolean allowSearch, boolean fetchIsrc, AudioPlayerManager playerManager) {
        this(allowSearch, fetchIsrc, DEFAULT_API_KEY, playerManager);
    }

    public NapsterAudioSourceManager(boolean allowSearch, boolean fetchIsrc, String apikey, AudioPlayerManager playerManager) {
        super(playerManager, fetchIsrc);
        this.apikey = apikey;
        this.allowSearch = allowSearch;
        
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
        this.httpInterfaceManager.setHttpContextFilter(new NapsterHttpContextFilter(this.apikey));
    }

    @Override
    public String getSourceName() {
        return "napster";
    }

    public String getApikey() {
        return this.apikey;
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference) {
        if (reference.identifier.startsWith(SEARCH_PREFIX) && allowSearch) {
            return this.loadSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
        }

        Matcher m;
        if (( m = napsterUrlPattern.matcher(reference.identifier) ).find()) {
            String track = m.group(3);
            String album = m.group(2);
            String artist = m.group(1);
            if (track != null) return this.loadTrack(artist, album, track, null);
            if (album != null) return this.loadAlbum(artist, album, null);
            if (artist != null) return this.loadArtist(artist);
        }

        if ((m = napsterAppPattern.matcher(reference.identifier) ).find()) {
            String id = m.group(2);
            switch (m.group(1)) {
                case "playlist": return this.loadPlaylist(id);
                case "track": return this.loadTrack(null, null, null, id);
                case "album": return this.loadAlbum(null, null, id);
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
        
        JsonBrowser json = this.requestApi(String.format(TRACK_ID_API_URL, identifier));
        String isrc = json.get("tracks").index(0).get("isrc").text();
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

    private AudioItem loadSearch(String query) {
        try {
            URI uri = new URIBuilder(SEARCH_API_URL)
            .addParameter("query", query)
            .addParameter("limit", "200")
            .addParameter("type", "track").build();

            JsonBrowser json = this.requestApi(uri);
            if(json.get("search").get("data").get("tracks").index(0).isNull()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();
            for(JsonBrowser track : json.get("search").get("data").get("tracks").values()) {
                tracks.add(buildTrack(track));
            }

            return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Napster search result", SUSPICIOUS, e);
        }
    }

    private AudioItem loadTrack(String artist, String album, String t, String id) {
        JsonBrowser track = this.requestApi(
            id != null
            ? String.format(TRACK_ID_API_URL, id)
            : String.format(TRACK_API_URL, artist, album, t)
        ).get("tracks").index(0);
        if (track.isNull()) {
            return AudioReference.NO_TRACK;
        }
        return buildTrack(track);
    }

    private AudioItem loadAlbum(String artist, String a, String id) {
        JsonBrowser album = this.requestApi(
            id != null
            ? String.format(ALBUM_ID_API_URL, id)
            : String.format(ALBUM_API_URL, artist, a)
        ).get("albums").index(0);
        if (album.isNull()) {
            return AudioReference.NO_TRACK;
        }
        String albumId = album.get("id").text();
        JsonBrowser json = this.requestApi(String.format(ALBUM_TRACK_API_URL, albumId));
        if (json.get("tracks").index(0).isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonBrowser track : json.get("tracks").values()) {
            tracks.add(buildTrack(track));
        }

        return new BasicAudioPlaylist(
            album.get("name").safeText(),
            album.get("artistName").safeText(),
            String.format(CDN_URL, albumId),
            ALBUM_URL + albumId,
            album.get("type").safeText(),
            tracks,
            null,
            false
        );
    }

    private AudioItem loadArtist(String id) {
        JsonBrowser artist = this.requestApi(String.format(ARTIST_API_URL, id)).get("artists").index(0);
        if (artist.isNull()) {
            return AudioReference.NO_TRACK;
        }
        String artistId = artist.get("id").text();
        JsonBrowser json = this.requestApi(String.format(ARTIST_TRACK_API_URL, artistId));
        if (json.get("tracks").index(0).isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonBrowser track : json.get("tracks").values()) {
            tracks.add(buildTrack(track));
        }

        return new BasicAudioPlaylist(
            artist.get("name").safeText(),
            artist.get("name").safeText(),
            null,
            ARTIST_URL + artistId,
            "artist",
            tracks,
            null,
            false
        );
    }

    private AudioItem loadPlaylist(String id) {
        JsonBrowser playlist = this.requestApi(String.format(PLAYLIST_API_URL, id)).get("playlists").index(0);
        if (playlist.isNull()) {
            return AudioReference.NO_TRACK;
        }
        JsonBrowser json = this.requestApi(String.format(ARTIST_TRACK_API_URL, id));
        if (json.get("tracks").index(0).isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonBrowser track : json.get("tracks").values()) {
            tracks.add(buildTrack(track));
        }

        return new BasicAudioPlaylist(
            playlist.get("name").safeText(),
            null,
            playlist.get("images").index(0).get("url").text(),
            PLAYLIST_URL + id,
            "playlist",
            tracks,
            null,
            false
        );
    }

    private AudioTrack buildTrack(JsonBrowser trackInfo) {
        String identifier = trackInfo.get("id").text();
        String albumId = trackInfo.get("albumId").text();
        AudioTrackInfo info = new AudioTrackInfo(
            trackInfo.get("name").safeText(),
            trackInfo.get("artistName").safeText(),
            (long) (trackInfo.get("playbackSeconds").as(Double.class) * 1000.0),
            identifier,
            false,
            TRACK_URL + identifier,
            albumId != null ? String.format(CDN_URL, albumId) : null
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
                throw new IOException("Napster api request failed with status code: " + statusCode);
            }

            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            return JsonBrowser.parse(responseText);
        } catch (IOException e) {
            throw new FriendlyException("Failed to make a request to Napster Api", SUSPICIOUS, e);
        }
    }
}
