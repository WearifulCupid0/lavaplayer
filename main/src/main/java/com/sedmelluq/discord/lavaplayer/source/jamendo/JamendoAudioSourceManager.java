package com.sedmelluq.discord.lavaplayer.source.jamendo;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.source.jamendo.JamendoConstants.DEFAULT_CLIENT_ID;;

/**
 * Audio source manager that implements finding Jamendo tracks based on URL.
 */
public class JamendoAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String TRACK_REGEX = "^(?:http://|https://|)www\\.jamendo\\.com/track/(\\d+)(?:/|/([^/]+)|)$";
    private static final String ALBUM_REGEX = "^(?:http://|https://|)www\\.jamendo\\.com/album/(\\d+)(?:/|/([^/]+)|)$";
    private static final String ARTIST_REGEX = "^(?:http://|https://|)www\\.jamendo\\.com/artist/(\\d+)(?:/|/([^/]+)|)$";
    private static final String PLAYLIST_REGEX = "^(?:http://|https://|)www\\.jamendo\\.com/playlist/(\\d+)(?:/|/([^/]+)|)$";

    private static final String SHORT_TRACK_REGEX = "^(?:http://|https://|)www\\.jamen\\.do/t/(\\d+)$";
    private static final String SHORT_ALBUM_REGEX = "^(?:http://|https://|)www\\.jamen\\.do/a/(\\d+)$";
    private static final String SHORT_ARTIST_REGEX = "^(?:http://|https://|)www\\.jamen\\.do/l/(\\d+)$";
    private static final String SHORT_PLAYLIST_REGEX = "^(?:http://|https://|)www\\.jamen\\.do/l/p(\\d+)$";

    private static final String SEARCH_REGEX = "jmsearch:";

    private static final Pattern trackPattern = Pattern.compile(TRACK_REGEX);
    private static final Pattern albumPattern = Pattern.compile(ALBUM_REGEX);
    private static final Pattern artistPattern = Pattern.compile(ARTIST_REGEX);
    private static final Pattern playlistPattern = Pattern.compile(PLAYLIST_REGEX);

    private static final Pattern shortTrackPattern = Pattern.compile(SHORT_TRACK_REGEX);
    private static final Pattern shortAlbumPattern = Pattern.compile(SHORT_ALBUM_REGEX);
    private static final Pattern shortArtistPattern = Pattern.compile(SHORT_ARTIST_REGEX);
    private static final Pattern shortPlaylistPattern = Pattern.compile(SHORT_PLAYLIST_REGEX);

    private final String clientId;
    private final boolean allowSearch;

    private JamendoApiLoader apiLoader = null;

    private final HttpInterfaceManager httpInterfaceManager;

    public JamendoAudioSourceManager() {
        this(true);
    }

    public JamendoAudioSourceManager(boolean allowSearch) {
        this(DEFAULT_CLIENT_ID, allowSearch);
    }

    public JamendoAudioSourceManager(
                String clientId,
                boolean allowSearch) {
            this.clientId = clientId;
            this.allowSearch = allowSearch;

            this.setApiLoader(new DefaultJamendoApiLoader(this));

            httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
            httpInterfaceManager.setHttpContextFilter(new JamendoHttpContextFilter(this));
    }

    public void setApiLoader(JamendoApiLoader apiLoader) {
        this.apiLoader = apiLoader;
    }

    @Override
    public String getSourceName() {
        return "jamendo";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if (this.apiLoader == null) return null;

        if (trackPattern.matcher(reference.identifier).matches() || shortTrackPattern.matcher(reference.identifier).matches()) {
            Matcher trackMatcher = trackPattern.matcher(reference.identifier);
            if (!trackMatcher.matches()) trackMatcher = shortTrackPattern.matcher(reference.identifier);
            String id = trackMatcher.group(1);
            return apiLoader.loadTrack(id);
        }
        if (albumPattern.matcher(reference.identifier).matches() || shortAlbumPattern.matcher(reference.identifier).matches()) {
            Matcher albumMatcher = albumPattern.matcher(reference.identifier);
            if (!albumMatcher.matches()) albumMatcher = shortAlbumPattern.matcher(reference.identifier);
            String id = albumMatcher.group(1);
            return apiLoader.loadAlbum(id);
        }
        if (artistPattern.matcher(reference.identifier).matches() || shortArtistPattern.matcher(reference.identifier).matches()) {
            Matcher artistMatcher = artistPattern.matcher(reference.identifier);
            if (!artistMatcher.matches()) artistMatcher = shortArtistPattern.matcher(reference.identifier);
            String id = artistMatcher.group(1);
            return apiLoader.loadArtist(id);
        }
        if (playlistPattern.matcher(reference.identifier).matches() || shortPlaylistPattern.matcher(reference.identifier).matches()) {
            Matcher playlistMatcher = playlistPattern.matcher(reference.identifier);
            if (!playlistMatcher.matches()) playlistMatcher = shortPlaylistPattern.matcher(reference.identifier);
            String id = playlistMatcher.group(1);
            return apiLoader.loadPlaylist(id);
        }
        if (allowSearch && reference.identifier.startsWith(SEARCH_REGEX)) {
            return apiLoader.loadSearchResults(reference.identifier.substring(SEARCH_REGEX.length()).trim());
        }

        return null;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        // No special values to encode
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new JamendoAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager);
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

    public ExtendedHttpConfigurable getMainHttpConfiguration() {
        return httpInterfaceManager;
    }

    public String getClientId() {
        return clientId;
    }
}