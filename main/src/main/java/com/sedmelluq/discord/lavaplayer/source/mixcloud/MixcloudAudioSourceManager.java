package com.sedmelluq.discord.lavaplayer.source.mixcloud;

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
 * Audio source manager that implements finding Mixcloud tracks based on URL.
 */
public class MixcloudAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private final String TRACK_REGEX = "(?:http://|https://|)?(?:(?:www|beta|m)\\.)?mixcloud\\.com/([^/]+)/([^/]+)";
    private final String PLAYLIST_REGEX = "(?:http://|https://|)?(?:(?:www|beta|m)\\.)?mixcloud\\.com/([^/]+)/playlists/([^/]+)";
    private final String ARTIST_REGEX = "(?:http://|https://|)?(?:(?:www|beta|m)\\.)?mixcloud\\.com/([^/]+)";
    
    private final String SEARCH_PREFIX = "mxsearch:";

    private final Pattern trackPattern = Pattern.compile(TRACK_REGEX);
    private final Pattern playlistPattern = Pattern.compile(PLAYLIST_REGEX);
    private final Pattern artistPattern = Pattern.compile(ARTIST_REGEX);

    private final boolean allowSearch;

    private final HttpInterfaceManager httpInterfaceManager;
    private final ExtendedHttpConfigurable combinedHttpConfiguration;

    private final MixcloudDirectUrlLoader directUrlLoader;
    private final MixcloudSearchProvider searchProvider;
    private final MixcloudDataLoader dataLoader;

    public MixcloudAudioSourceManager() {
        this(true);
    }

    public MixcloudAudioSourceManager(boolean allowSearch) {
        this(allowSearch, new DefaultMixcloudDirectUrlLoader(), new DefaultMixcloudDataLoader(), new DefaultMixcloudSearchResultLoader());
    }

    public MixcloudAudioSourceManager(
            boolean allowSearch,
            MixcloudDirectUrlLoader directUrlLoader,
            MixcloudDataLoader dataLoader,
            MixcloudSearchProvider searchProvider) {
        this.allowSearch = allowSearch;

        this.directUrlLoader = directUrlLoader;
        this.dataLoader = dataLoader;
        this.searchProvider = searchProvider;

        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();

        combinedHttpConfiguration = new MultiHttpConfigurable(Arrays.asList(
            httpInterfaceManager,
            dataLoader.getHttpConfiguration()
        ));
    }
    @Override
    public String getSourceName() {
        return "mixcloud";
    }

    @Override
    public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
        Matcher matcher;
        if (( matcher = playlistPattern.matcher(reference.identifier) ).find()) {
            return dataLoader.getPlaylist(matcher.group(2), matcher.group(1), this::getTrack);
        }
        if (( matcher = trackPattern.matcher(reference.identifier) ).find()) {
            return dataLoader.getTrack(matcher.group(2), matcher.group(1), this::getTrack);
        }
        if (( matcher = artistPattern.matcher(reference.identifier) ).find()) {
            return dataLoader.getArtist(matcher.group(1), this::getTrack);
        }
        if (allowSearch && reference.identifier.startsWith(SEARCH_PREFIX)) {
            return searchProvider.loadSearchResults(reference.identifier.substring(SEARCH_PREFIX.length()).trim(), this::getTrack);
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
        return new MixcloudAudioTrack(trackInfo, this);
    }

    public AudioTrack getTrack(AudioTrackInfo info) {
        return new MixcloudAudioTrack(info, this);
    }

    public MixcloudDirectUrlLoader getDirectUrlLoader() {
        return this.directUrlLoader;
    }

    @Override
    public void shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager);
        dataLoader.shutdown();
    }

    /**
    * @return Get an HTTP interface for a playing track.
    */

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        combinedHttpConfiguration.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        combinedHttpConfiguration.configureBuilder(configurator);
    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    public ExtendedHttpConfigurable getDataLoaderHttpConfiguration() {
        return dataLoader.getHttpConfiguration();
    }
}