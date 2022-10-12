package com.sedmelluq.discord.lavaplayer.source.jiosaavn;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.http.MultiHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.*;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;

public class JioSaavnAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private final static String SONG_REGEX = "^(?:http://|https://|)(?:www\\.|)jiosaavn\\.com/song/(?:.*)/([a-zA-Z0-9-_]+)$";
    private final static String ALBUM_REGEX = "^(?:http://|https://|)(?:www\\.|)jiosaavn\\.com/album/(?:.*)/([a-zA-Z0-9-_]+)$";
    private final static String PLAYLIST_REGEX = "^(?:http://|https://|)(?:www\\.|)jiosaavn\\.com/featured/(?:.*)/([a-zA-Z0-9-_]+)$";

    private final static String SEARCH_PREFIX = "jssearch:";

    private final static Pattern songPattern = Pattern.compile(SONG_REGEX);
    private final static Pattern albumPattern = Pattern.compile(ALBUM_REGEX);
    private final static Pattern playlistPattern = Pattern.compile(PLAYLIST_REGEX);

    private final boolean allowSearch;
    private final JioSaavnApiHandler apiHandler;

    private final HttpInterfaceManager httpInterfaceManager;
    private final ExtendedHttpConfigurable combinedHttpConfiguration;

    /**
     * Create an instance.
     */
    public JioSaavnAudioSourceManager() {
        this(true);
    }

    public JioSaavnAudioSourceManager(boolean allowSearch) {
        this(allowSearch, new DefaultJioSaavnApiHandler());
    }

    public JioSaavnAudioSourceManager(boolean allowSearch, JioSaavnApiHandler apiHandler) {
        this.allowSearch = allowSearch;
        this.apiHandler = apiHandler;

        httpInterfaceManager = new ThreadLocalHttpInterfaceManager(
                HttpClientTools.createSharedCookiesHttpBuilder(),
                RequestConfig.custom()
                        .setConnectTimeout(15000)
                        .build()
        );
        combinedHttpConfiguration = new MultiHttpConfigurable(Arrays.asList(
                httpInterfaceManager,
                apiHandler.getHttpConfiguration()
        ));
    }

    @Override
    public String getSourceName() {
        return "jiosaavn";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        Matcher matcher;

        if ((matcher = songPattern.matcher(reference.identifier)).matches()) {
            return apiHandler.track(matcher.group(1), this::getTrack);
        }
        if ((matcher = albumPattern.matcher(reference.identifier)).matches()) {
            return apiHandler.album(matcher.group(1), this::getTrack);
        }
        if ((matcher = playlistPattern.matcher(reference.identifier)).matches()) {
            return apiHandler.playlist(matcher.group(1), this::getTrack);
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
        return new JioSaavnAudioTrack(trackInfo, this);
    }

    public AudioTrack getTrack(AudioTrackInfo trackInfo) {
        return new JioSaavnAudioTrack(trackInfo, this);
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

    public ExtendedHttpConfigurable getHttpConfiguration() {
        return combinedHttpConfiguration;
    }

    public ExtendedHttpConfigurable getApiHandlerHttpConfiguration() {
        return apiHandler.getHttpConfiguration();
    }
}