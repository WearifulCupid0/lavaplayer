package com.sedmelluq.discord.lavaplayer.source.iheart;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.http.MultiHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;

public class iHeartAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String RADIO_REGEX = "^(?:http://|https://|)(?:www\\.|)iheart\\.com/live/(\\d+)";
    private static final String PODCAST_REGEX = "^(?:http://|https://|)(?:www\\.|)iheart\\.com/podcast/(?:[a-zA-Z0-9-_]+)-(\\d+)";
    private static final String EPISODE_REGEX = PODCAST_REGEX + "/episode/(?:[a-zA-Z0-9-_]+)-(\\d+)";

    private static final String SEARCH_PREFIX = "ihsearch:";

    private static final Pattern radioPattern = Pattern.compile(RADIO_REGEX);
    private static final Pattern podcastPattern = Pattern.compile(PODCAST_REGEX);
    private static final Pattern episodePattern = Pattern.compile(EPISODE_REGEX);

    private final boolean allowSearch;

    private final iHeartApiHandler apiHandler;
    private final HttpInterfaceManager httpInterfaceManager;
    private final MediaContainerRegistry containerRegistry;
    private final ExtendedHttpConfigurable combinedHttpConfiguration;

    /**
    * Create an instance.
    */
    public iHeartAudioSourceManager() {
        this(true);
    }

    public iHeartAudioSourceManager(boolean allowSearch) {
        this(allowSearch, MediaContainerRegistry.DEFAULT_REGISTRY);
    }

    public iHeartAudioSourceManager(boolean allowSearch, MediaContainerRegistry containerRegistry) {
        this(allowSearch, containerRegistry, new DefaultiHeartApiHandler());
    }

    public iHeartAudioSourceManager(boolean allowSearch, MediaContainerRegistry containerRegistry, iHeartApiHandler apiHandler) {
        this.allowSearch = allowSearch;
        this.apiHandler = apiHandler;
        this.containerRegistry = containerRegistry;

        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
        combinedHttpConfiguration = new MultiHttpConfigurable(Arrays.asList(
            httpInterfaceManager,
            apiHandler.getHttpConfiguration()
        ));
    }

    @Override
    public String getSourceName() {
        return "iheart";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        Matcher matcher;

        if ((matcher = radioPattern.matcher(reference.identifier)).find()) {
            return apiHandler.radio(matcher.group(1), this::getTrack);
        }

        if ((matcher = episodePattern.matcher(reference.identifier)).find()) {
            return apiHandler.podcast(matcher.group(1), matcher.group(2), this::getTrack);
        }

        if ((matcher = podcastPattern.matcher(reference.identifier)).find()) {
            return apiHandler.podcast(matcher.group(1), null, this::getTrack);
        }

        if (allowSearch && reference.identifier.startsWith(SEARCH_PREFIX)) {
            return apiHandler.search(reference.identifier.substring(SEARCH_PREFIX.length()).trim(), this::getTrack);
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
        return new iHeartAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager);
    }

    public AudioTrack getTrack(AudioTrackInfo trackInfo) {
        return new iHeartAudioTrack(trackInfo, this);
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

    public ExtendedHttpConfigurable getHttpConfiguration() {
        return combinedHttpConfiguration;
    }

    public ExtendedHttpConfigurable getApiHandlerHttpConfiguration() {
        return apiHandler.getHttpConfiguration();
    }

    public MediaContainerRegistry getMediaContainerRegistry() {
        return containerRegistry;
    }
}