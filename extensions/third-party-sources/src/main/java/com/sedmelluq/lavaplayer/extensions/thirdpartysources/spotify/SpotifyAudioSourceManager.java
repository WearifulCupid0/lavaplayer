package com.sedmelluq.lavaplayer.extensions.thirdpartysources.spotify;

import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.*;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;

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

import static com.sedmelluq.lavaplayer.extensions.thirdpartysources.spotify.SpotifyConstants.*;
import static com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class SpotifyAudioSourceManager extends ThirdPartyAudioSourceManager implements HttpConfigurable {
    private static final String SPOTIFY_URL_REGEX = "^(?:https://|http://|)(?:www\\.|open\\.|)spotify\\.com/(?:user/[a-zA-Z0-9-_]+/|)(track|album|playlist|artist)/([a-zA-Z0-9-_]+)";
	private static final String SPOTIFY_URN_REGEX = "^spotify:(?:user:[a-zA-Z0-9-_]+:|)(track|album|playlist|artist):([a-zA-Z0-9-_]+)";
    
    private static final Pattern spotifyUrlPattern = Pattern.compile(SPOTIFY_URL_REGEX);
    private static final Pattern spotifyUrnPattern = Pattern.compile(SPOTIFY_URN_REGEX);

    private static final String SIMILAR_PREFIX = "spsimilar:";
    private static final String SEARCH_PREFIX = "spsearch:";
    private static final String TRACKS_PREFIX = "sptracks:";

    private final boolean allowSearch;
    private final SpotifyTokenTracker tokenTracker;
    private final HttpInterfaceManager httpInterfaceManager;

    public SpotifyAudioSourceManager(AudioPlayerManager playerManager) {
        this(true, playerManager);
    }

    public SpotifyAudioSourceManager(boolean allowSearch, AudioPlayerManager playerManager) {
        this(allowSearch, null, null, playerManager);
    }

    public SpotifyAudioSourceManager(boolean allowSearch, String clientId, String clientSecret, AudioPlayerManager playerManager) {
        super(playerManager);
        this.allowSearch = allowSearch;
        
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
        this.tokenTracker = new SpotifyTokenTracker(httpInterfaceManager, clientId, clientSecret);
        this.httpInterfaceManager.setHttpContextFilter(new SpotifyHttpContextFilter(this.tokenTracker));
    }

    @Override
    public String getSourceName() {
        return "spotify";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference) {
        if (reference.identifier.startsWith(SEARCH_PREFIX) && allowSearch) {
            return this.loadSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
        }

        if (reference.identifier.startsWith(SIMILAR_PREFIX)) {
            return this.loadSimilar(reference.identifier.substring(SIMILAR_PREFIX.length()).trim());
        }

        if (reference.identifier.startsWith(TRACKS_PREFIX)) {
            return this.loadTracks(reference.identifier.substring(TRACKS_PREFIX.length()).trim());
        }

        Matcher matcher;
        if ((matcher = spotifyUrlPattern.matcher(reference.identifier)).find()) {
            return this.load(matcher.group(2), matcher.group(1));
        }
        if ((matcher = spotifyUrnPattern.matcher(reference.identifier)).find()) {
            return this.load(matcher.group(2), matcher.group(1));
        }

        return null;
    }

    private AudioItem load(String id, String type) {
        switch (type) {
			case "album": return this.loadAlbum(id);
			case "track": return this.loadTrack(id);
			case "playlist": return this.loadPlaylist(id);
			case "artist": return this.loadArtist(id);
            default: return null;
		}
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void shutdown() {
        //nothing to shutdown
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

    public SpotifyTokenTracker getTokenTracker() {
        return this.tokenTracker;
    }

    private AudioItem loadSimilar(String id) {
        try {
            URI uri = new URIBuilder(SIMILAR_API_URL)
            .addParameter("seed_tracks", id)
            .addParameter("limit", "100").build();

            JsonBrowser result = this.requestApi(uri);
            if (result.isNull()) {
                return AudioReference.NO_TRACK;
            }
            List<AudioTrack> tracks = new ArrayList<>();
            result.get("tracks").values().forEach(info -> {
                AudioTrack track = buildTrack(info, info.get("album"));
                if (track != null) tracks.add(track);
            });

            return new BasicAudioPlaylist("Loaded similar tracks for id: " + id, null, null, null, "similar", tracks, null, true);
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Spotify similar result", SUSPICIOUS, e);
        }
    }

    private AudioItem loadTracks(String ids) {
        JsonBrowser result = this.requestApi(TRACKS_API_URL + ids);
        if(result.isNull()) {
            return AudioReference.NO_TRACK;
        }
        List<AudioTrack> tracks = new ArrayList<>();
        result.get("tracks").values().forEach(info -> {
            AudioTrack track = buildTrack(info, info.get("album"));
            if (track != null) tracks.add(track);
        });

        return new BasicAudioPlaylist("Loaded tracks: " + ids, null, null, null, "tracks", tracks, null, true);
    }

    private AudioItem loadTrack(String id) {
        JsonBrowser track = this.requestApi(TRACK_API_URL + id);
        if(track.isNull()) {
            return AudioReference.NO_TRACK;
        }
        return buildTrack(track, track.get("album"));
    }

    private AudioItem loadArtist(String id) {
        JsonBrowser artist = this.requestApi(ARTIST_API_URL + id);
        if(artist.isNull()) {
            return AudioReference.NO_TRACK;
        }
        JsonBrowser json = this.requestApi(String.format(ARTIST_TRACKS_API_URL, id));

        if(json.get("tracks").index(0).isNull()) {
            return AudioReference.NO_TRACK;
        }
        
        List<AudioTrack> tracks = new ArrayList<>();
        for(JsonBrowser track : json.get("tracks").values()) {
            tracks.add(buildTrack(track, track.get("album")));
        }
        return new BasicAudioPlaylist(
            artist.get("name").safeText(),
            artist.get("name").safeText(),
            pickBestArtwork(artist.get("images").values()),
            ARTIST_URL + id,
            "artist",
            tracks,
            null,
            false
        );
    }

    private AudioItem loadAlbum(String id) {
        JsonBrowser album = this.requestApi(ALBUM_API_URL + id + "?limit=50");
        if(album.isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for(JsonBrowser track : album.get("tracks").get("items").values()) {
            tracks.add(buildTrack(track, album));
        }
        String next = album.get("tracks").get("next").text();
        while(next != null) {
            JsonBrowser tPage = this.requestApi(next);
            for(JsonBrowser track : tPage.get("items").values()) {
                tracks.add(buildTrack(track, album));
            }
            next = tPage.get("next").text();
        }
        return new BasicAudioPlaylist(
            album.get("name").text(),
            album.get("artists").index(0).get("name").text(),
            pickBestArtwork(album.get("images").values()),
            ALBUM_URL + id,
            album.get("album_type").safeText().toLowerCase(),
            tracks,
            null,
            false
        );
    }

    private AudioItem loadPlaylist(String id) {
        JsonBrowser playlist = this.requestApi(PLAYLIST_API_URL + id + "?limit=100");
        if(playlist.isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for(JsonBrowser track : playlist.get("tracks").get("items").values()) {
            tracks.add(buildTrack(track.get("track"), track.get("track").get("album")));
        }
        String next = playlist.get("tracks").get("next").text();
        while(next != null) {
            JsonBrowser tPage = this.requestApi(next);
            for(JsonBrowser item : tPage.get("items").values()) {
                if (!item.get("track").isNull()) tracks.add(buildTrack(item.get("track"), item.get("track").get("album")));
            }
            next = tPage.get("next").text();
        }
        return new BasicAudioPlaylist(
            playlist.get("name").text(),
            playlist.get("owner").get("display_name").text(),
            pickBestArtwork(playlist.get("images").values()),
            PLAYLIST_URL + id,
            "playlist",
            tracks,
            null,
            false
        );
    }

    private AudioItem loadSearch(String query) {
        try {
            URI uri = new URIBuilder(SEARCH_API_URL)
            .addParameter("type", "track")
            .addParameter("limit", "50")
            .addParameter("q", query).build();

            JsonBrowser json = this.requestApi(uri);
            if(json.get("tracks").get("items").index(0).isNull()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();
            for(JsonBrowser track : json.get("tracks").get("items").values()) {
                tracks.add(buildTrack(track, track.get("album")));
            }

            return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Spotify search result", SUSPICIOUS, e);
        }
    }

    private AudioTrack buildTrack(JsonBrowser trackInfo, JsonBrowser albumInfo) {
        String identifier = trackInfo.get("is_local").asBoolean(false) //Local tracks usually set id as null
        ? trackInfo.get("uri").safeText().replace("spotify:local:", "")
        : trackInfo.get("id").safeText();
        List<JsonBrowser> images = albumInfo.get("images").values();

        String isrc = trackInfo.get("external_ids").get("isrc").text();

        List<AudioTrackAuthorInfo> artists = new ArrayList<>();
        for (JsonBrowser artist : trackInfo.get("artists").values()) {
            String url = artist.get("id").text();
            if (!SourceTools.isBlank(url))
                url = ARTIST_URL + url;
            artists.add(new AudioTrackAuthorInfo(artist.get("name").safeText(), url));
        }

        AudioTrackInfo info = new AudioTrackInfo(
            trackInfo.get("name").safeText(),
            artists,
            trackInfo.get("duration_ms").asLong(DURATION_MS_UNKNOWN),
            identifier,
            false,
            !trackInfo.get("is_local").asBoolean(false)
            ? TRACK_URL + identifier
            : null,
            pickBestArtwork(images),
            trackInfo.get("explicit").asBoolean(false),
            isrc
        );

        return new ThirdPartyAudioTrack(info, this);
    }

    private String pickBestArtwork(List<JsonBrowser> artworks) {
        int resolution = 0;
        String url = null;

        for (JsonBrowser artworkData : artworks) {
            int width = artworkData.get("width").asInt(0);
            int height = artworkData.get("height").asInt(0);

            int sum = width + height;
            if (resolution < sum) {
                resolution = sum;
                url = artworkData.get("url").text();
            }
        }

        return url;
    }

    private JsonBrowser requestApi(String uri) {
        return this.requestApi(URI.create(uri));
    }

    private JsonBrowser requestApi(URI uri) {
        try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(uri))) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("Spotify api request failed with status code: " + statusCode);
            }

            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            return JsonBrowser.parse(responseText);
        } catch (IOException e) {
            throw new FriendlyException("Failed to make a request to Spotify Api", SUSPICIOUS, e);
        }
    }
}