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
    private static final String URL_REGEX = "^(?:http://|https://|)www\\.jamendo\\.com/([a-zA-Z0-9-_]+)/([0-9]+)$";
    private static final String SHORT_URL_REGEX = "^(?:http://|https://|)www\\.jamen\\.do/([a-zA-Z0-9-_]+)/(p|)([0-9]+)$";

    private static final String CLIENT_ID = "c7b47146";

    private static final Pattern urlPattern = Pattern.compile(URL_REGEX);
    private static final Pattern shortUrlPattern = Pattern.compile(SHORT_URL_REGEX);

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
        Matcher urlMatcher = urlPattern.matcher(reference.identifier);
        if(urlMatcher.matches()) {
            String type = urlMatcher.group(1);
            String id = urlMatcher.group(2);
            if (type == "track") return trackLoader.loadTrack(id, CLIENT_ID, this::getTrack);
            if (type == "album") return playlistLoader.loadPlaylist(id, type, CLIENT_ID, this::getTrack);
            if (type == "artist") return playlistLoader.loadPlaylist(id, type, CLIENT_ID, this::getTrack);
            if (type == "playlist") return playlistLoader.loadPlaylist(id, type, CLIENT_ID, this::getTrack);
        };
        Matcher shortUrlMatcher = shortUrlPattern.matcher(reference.identifier);
        if(shortUrlMatcher.matches()) {
            String type = urlMatcher.group(1);
            String id = urlMatcher.group(2);
            if (type == "t") return trackLoader.loadTrack(id, CLIENT_ID, this::getTrack);
            if (type == "a") return playlistLoader.loadPlaylist(id, "album", CLIENT_ID, this::getTrack);
            if (type == "l" && id.startsWith("p")) return playlistLoader.loadPlaylist(id.replace("p", ""), "playlist", CLIENT_ID, this::getTrack);
            else if (type == "l") return playlistLoader.loadPlaylist(id, "artist", CLIENT_ID, this::getTrack);
        };
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
