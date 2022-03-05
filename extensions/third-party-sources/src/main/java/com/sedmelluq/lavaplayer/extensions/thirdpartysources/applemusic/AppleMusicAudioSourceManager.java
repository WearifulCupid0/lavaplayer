package com.sedmelluq.lavaplayer.extensions.thirdpartysources.applemusic;

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

import static com.sedmelluq.lavaplayer.extensions.thirdpartysources.applemusic.AppleMusicConstants.*;
import static com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class AppleMusicAudioSourceManager extends ThirdPartyAudioSourceManager implements HttpConfigurable {
    private static final String APPLEMUSIC_URL_REGEX = "^(?:https://|http://|)(?:www\\.|)music\\.apple\\.com/(?:[a-zA-Z]{2}/)(?<type>artist|playlist|album)/(?:[a-zA-Z0-9\\-]+/|)(?<identifier>[a-zA-Z0-9-_\\.]+)";
    private static final String TRACK_ID_REGEX = "i=(\\d+)";

    private static final Pattern appleMusicUrlPattern = Pattern.compile(APPLEMUSIC_URL_REGEX);
    private static final Pattern trackIdPattern = Pattern.compile(TRACK_ID_REGEX);

    private static final String SEARCH_PREFIX = "amsearch:";

    private final Map<String, String> isrcCache = new HashMap<>();
    private final boolean allowSearch;
    private final AppleMusicTokenTracker tokenTracker;
    private final HttpInterfaceManager httpInterfaceManager;

    public AppleMusicAudioSourceManager(AudioPlayerManager playerManager) {
        this(true, true, playerManager);
    }

    public AppleMusicAudioSourceManager(boolean allowSearch, AudioPlayerManager playerManager) {
        this(allowSearch, true, playerManager);
    }

    public AppleMusicAudioSourceManager(boolean allowSearch, boolean fetchIsrc, AudioPlayerManager playerManager) {
        super(playerManager, fetchIsrc);
        this.allowSearch = allowSearch;
        
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
        this.tokenTracker = new AppleMusicTokenTracker(httpInterfaceManager);
        this.httpInterfaceManager.setHttpContextFilter(new AppleMusicHttpContextFilter(this.tokenTracker));
    }

    @Override
    public String getSourceName() {
        return "apple-music";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference) {
        if (reference.identifier.startsWith(SEARCH_PREFIX) && allowSearch) {
            return this.loadSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
        }

        Matcher matcher = appleMusicUrlPattern.matcher(reference.identifier);
        if (matcher.find()) {
            String id = matcher.group("identifier");
            
            switch (matcher.group("type")) {
                case "album": {
                    Matcher trackIdMatcher = trackIdPattern.matcher(reference.identifier);
                    String trackId = null;
                    if (trackIdMatcher.find()) {
                        trackId = trackIdMatcher.group(1);
                    }
                    return this.loadAlbumOrTrack(id, trackId);
                }
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
        String isrc = json.get("data").index(0).get("attributes").get("isrc").text();
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

    public AppleMusicTokenTracker getTokenTracker() {
        return this.tokenTracker;
    }

    private AudioItem loadSearch(String query) {
        try {
            URI uri = new URIBuilder(SEARCH_API_URL)
            .addParameter("limit", "25")
            .addParameter("term", query).build();

            JsonBrowser json = this.requestApi(uri);
            if(json.get("results").get("songs").get("data").index(0).isNull()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();
            for(JsonBrowser track : json.get("results").get("songs").get("data").values()) {
                tracks.add(buildTrack(track));
            }

            return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
        } catch (Exception e) {
            throw new FriendlyException("Failed to load AppleMusic search result", SUSPICIOUS, e);
        }
    }

    private AudioItem loadPlaylist(String playlistId) {
        JsonBrowser playlist = this.requestApi(String.format(PLAYLIST_API_URL, playlistId)).get("data").index(0);
        if(playlist.isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        JsonBrowser tracksJson = playlist.get("relationships").get("tracks");
        for(JsonBrowser track : tracksJson.get("data").values()) {
            tracks.add(buildTrack(track));
        }
        String next = tracksJson.get("next").text();
        while(next != null && !next.isEmpty()) {
            tracksJson = this.requestApi(BASE_URL + next);
            next = tracksJson.get("next").text();
            for(JsonBrowser track : tracksJson.get("data").values()) {
                tracks.add(buildTrack(track));
            }
        }
        JsonBrowser attributes = playlist.get("attributes");
        return new BasicAudioPlaylist(
            attributes.get("name").safeText(),
            attributes.get("curatorName").safeText(),
            attributes.get("artwork").get("url").safeText().replace("{w}x{h}", "800x800"),
            attributes.get("url").text(),
            "playlist",
            tracks,
            null,
            false
        );
    }

    private AudioItem loadAlbumOrTrack(String albumId, String trackId) {
        JsonBrowser album = this.requestApi(String.format(ALBUM_API_URL, albumId)).get("data").index(0);
        if(album.isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        JsonBrowser tracksJson = album.get("relationships").get("tracks");
        for(JsonBrowser track : tracksJson.get("data").values()) {
            tracks.add(buildTrack(track));
        }
        String next = tracksJson.get("next").text();
        while(next != null && !next.isEmpty()) {
            tracksJson = this.requestApi(BASE_URL + next);
            next = tracksJson.get("next").text();
            for(JsonBrowser track : tracksJson.get("data").values()) {
                tracks.add(buildTrack(track));
            }
        }
        JsonBrowser attributes = album.get("attributes");
        return new BasicAudioPlaylist(
            attributes.get("name").safeText(),
            attributes.get("artistName").safeText(),
            attributes.get("artwork").get("url").safeText().replace("{w}x{h}", "800x800"),
            attributes.get("url").text(),
            attributes.get("isSingle").asBoolean(false) ? "single" : "album",
            tracks,
            findSelectedTrack(tracks, trackId),
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

    private AudioItem loadArtist(String artistId) {
        JsonBrowser artist = this.requestApi(String.format(ARTIST_API_URL, artistId)).get("data").index(0).get("attributes");
        if(artist.isNull()) {
            return AudioReference.NO_TRACK;
        }

        JsonBrowser json = this.requestApi(String.format(ARTIST_TRACK_API_URL, artistId));
        if (json.get("data").index(0).isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for(JsonBrowser track : json.get("data").values()) {
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

    private AudioTrack buildTrack(JsonBrowser trackInfo) {
        JsonBrowser attributes = trackInfo.get("attributes");
        String identifier = trackInfo.get("id").text();
        AudioTrackInfo info = new AudioTrackInfo(
            attributes.get("name").safeText(),
            attributes.get("artistName").safeText(),
            attributes.get("durationInMillis").asLong(DURATION_MS_UNKNOWN),
            identifier,
            false,
            attributes.get("url").text(),
            attributes.get("artwork").get("url").text().replace("{w}x{h}", "800x800")
        );

        String isrc = attributes.get("isrc").text();
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
                throw new IOException("AppleMusic api request failed with status code: " + statusCode);
            }

            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            return JsonBrowser.parse(responseText);
        } catch (IOException e) {
            throw new FriendlyException("Failed to make a request to AppleMusic Api", SUSPICIOUS, e);
        }
    }
}
