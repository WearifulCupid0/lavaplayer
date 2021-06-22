package com.sedmelluq.discord.lavaplayer.source.jamendo;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.http.MultiHttpConfigurable;
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
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Audio source manager that implements finding Jamendo tracks based on URL.
 */
public class JamendoAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String TRACK_REGEX = "^(?:http://|https://|)www\\.jamendo\\.com/track/([0-9-_]+)(?:\\?.*|)$";
    private static final String ALBUM_REGEX = "^(?:http://|https://|)www\\.jamendo\\.com/album/([0-9-_]+)(?:\\?.*|)$";
    private static final String ARTIST_REGEX = "^(?:http://|https://|)www\\.jamendo\\.com/artist/([0-9-_]+)(?:\\?.*|)$";
    private static final String PLAYLIST_REGEX = "^(?:http://|https://|)www\\.jamendo\\.com/playlist/([0-9-_]+)(?:\\?.*|)$";

    private static final String SHORT_TRACK_REGEX = "^(?:http://|https://|)www\\.jamen\\.do/t/([0-9-_]+)(?:\\?.*|)$";
    private static final String SHORT_ALBUM_REGEX = "^(?:http://|https://|)www\\.jamen\\.do/a/([0-9-_]+)(?:\\?.*|)$";
    private static final String SHORT_ARTIST_REGEX = "^(?:http://|https://|)www\\.jamen\\.do/l/([0-9-_]+)(?:\\?.*|)$";
    private static final String SHORT_PLAYLIST_REGEX = "^(?:http://|https://|)www\\.jamen\\.do/l/p([0-9-_]+)(?:\\?.*|)$";

    private static final String CLIENT_ID = "c7b47146";

    private static final Pattern trackPattern = Pattern.compile(TRACK_REGEX);
    private static final Pattern albumPattern = Pattern.compile(ALBUM_REGEX);
    private static final Pattern artistPattern = Pattern.compile(ARTIST_REGEX);
    private static final Pattern playlistPattern = Pattern.compile(PLAYLIST_REGEX);

    private static final Pattern shortTrackPattern = Pattern.compile(SHORT_TRACK_REGEX);
    private static final Pattern shortAlbumPattern = Pattern.compile(SHORT_ALBUM_REGEX);
    private static final Pattern shortArtistPattern = Pattern.compile(SHORT_ARTIST_REGEX);
    private static final Pattern shortPlaylistPattern = Pattern.compile(SHORT_PLAYLIST_REGEX);

    private final boolean allowSearch;

    private final HttpInterfaceManager httpInterfaceManager;
    private final ExtendedHttpConfigurable combinedHttpConfiguration;

    private final JamendoTrackLoader trackLoader;
    private final JamendoPlaylistLoader playlistLoader;
    private final JamendoSearchResultLoader searchResultLoader;

    public JamendoAudioSourceManager() {
        this(true);
    }

    public JamendoAudioSourceManager(boolean allowSearch) {
        this(
            allowSearch,
            new DefaultJamendoTrackLoader(),
            new DefaultJamendoPlaylistLoader(),
            new DefaultJamendoSearchProvider()
        );
    }

    public JamendoAudioSourceManager(
                boolean allowSearch,
                JamendoTrackLoader trackLoader,
                JamendoPlaylistLoader playlistLoader,
                JamendoSearchResultLoader searchResultLoader) {
            this.allowSearch = allowSearch;
            this.trackLoader = trackLoader;
            this.playlistLoader = playlistLoader;
            this.searchResultLoader = searchResultLoader;

            httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
            httpInterfaceManager.setHttpContextFilter(new JamendoHttpContextFilter());

            combinedHttpConfiguration = new MultiHttpConfigurable(Arrays.asList(
                httpInterfaceManager,
                trackLoader.getHttpConfiguration(),
                playlistLoader.getHttpConfiguration(),
                searchResultLoader.getHttpConfiguration()
            ));
    }
    @Override
    public String getSourceName() {
        return "jamendo";
    }

    @Override
    public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
        if (trackPattern.matcher(reference.identifier).matches() || shortTrackPattern.matcher(reference.identifier).matches()) {
            Matcher trackMatcher = trackPattern.matcher(reference.identifier);
            if (trackMatcher.matches()) trackMatcher = shortTrackPattern.matcher(reference.identifier);
            String id = trackMatcher.group(1);
            return trackLoader.loadTrack(id, CLIENT_ID, this::getTrack);
        }
        if (albumPattern.matcher(reference.identifier).matches() || shortAlbumPattern.matcher(reference.identifier).matches()) {
            Matcher albumMatcher = albumPattern.matcher(reference.identifier);
            if (albumMatcher.matches()) albumMatcher = shortAlbumPattern.matcher(reference.identifier);
            String id = albumMatcher.group(1);
            return playlistLoader.loadPlaylist(id, "album", CLIENT_ID, this::getTrack);
        }
        if (artistPattern.matcher(reference.identifier).matches() || shortArtistPattern.matcher(reference.identifier).matches()) {
            Matcher artistMatcher = artistPattern.matcher(reference.identifier);
            if (artistMatcher.matches()) artistMatcher = shortArtistPattern.matcher(reference.identifier);
            String id = artistMatcher.group(1);
            return playlistLoader.loadPlaylist(id, "artist", CLIENT_ID, this::getTrack);
        }
        if (playlistPattern.matcher(reference.identifier).matches() || shortPlaylistPattern.matcher(reference.identifier).matches()) {
            Matcher playlistMatcher = playlistPattern.matcher(reference.identifier);
            if (playlistMatcher.matches()) playlistMatcher = shortPlaylistPattern.matcher(reference.identifier);
            String id = playlistMatcher.group(1);
            return playlistLoader.loadPlaylist(id, "playlist", CLIENT_ID, this::getTrack)
        }
        if (allowSearch) {
            return searchResultLoader.loadSearchResult(reference.identifier, CLIENT_ID, this::getTrack);
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

    public AudioTrack getTrack(AudioTrackInfo info) {
        return new JamendoAudioTrack(info, this);
    }

    @Override
    public void shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager);
        trackLoader.shutdown();
        playlistLoader.shutdown();
        searchResultLoader.shutdown();
    }

    /**
    * @return Get an HTTP interface for a playing track.
    */
    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        combinedHttpConfiguration.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        combinedHttpConfiguration.configureBuilder(configurator);
    }

    public ExtendedHttpConfigurable getHttpConfiguration() {
        return combinedHttpConfiguration;
    }

    public ExtendedHttpConfigurable getMainHttpConfiguration() {
        return httpInterfaceManager;
    }

    public ExtendedHttpConfigurable getTrackLHttpConfiguration() {
        return trackLoader.getHttpConfiguration();
    }

    public ExtendedHttpConfigurable getPlaylistLHttpConfiguration() {
        return playlistLoader.getHttpConfiguration();
    }

    public ExtendedHttpConfigurable getSearchHttpConfiguration() {
        return searchResultLoader.getHttpConfiguration();
    }
}
