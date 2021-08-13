package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.client.config.RequestConfig;

public class MixcloudAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private final String TRACK_REGEX = "^(?:http://|https://|)?(?:(?:www|beta|m)\\.)?mixcloud\\.com/([a-zA-Z-_]+)/([a-zA-Z-_]+)";
    private final String PLAYLIST_REGEX = "^(?:http://|https://|)?(?:(?:www|beta|m)\\.)?mixcloud\\.com/([a-zA-Z-_]+)/playlists/([a-zA-Z-_]+)";
    private final String ARTIST_REGEX = "^(?:http://|https://|)?(?:(?:www|beta|m)\\.)?mixcloud\\.com/([a-zA-Z-_]+)";
    
    private final String SEARCH_PREFIX = "mxsearch:";

    private final Pattern trackPattern = Pattern.compile(TRACK_REGEX);
    private final Pattern playlistPattern = Pattern.compile(PLAYLIST_REGEX);
    private final Pattern artistPattern = Pattern.compile(ARTIST_REGEX);

    private final boolean allowSearch;

    public final MixcloudDataReader dataReader;
    public final MixcloudFormatHandler formatHandler;
    private MixcloudGraphqlHandler graphqlHandler = null;
    private final HttpInterfaceManager httpInterfaceManager;

    /**
     * Create an instance.
     */
    public MixcloudAudioSourceManager() {
        this(true);
    }

    public MixcloudAudioSourceManager(boolean allowSearch) {
        this(allowSearch, new DefaultMixcloudDataReader(), new DefaultMixcloudFormatHandler());
        this.setGraphqlHandler(new DefaultMixcloudGraphqlHandler(this));
    }

    public MixcloudAudioSourceManager(
        boolean allowSearch,
        MixcloudDataReader dataReader,
        MixcloudFormatHandler formatHandler
    ) {
        this.allowSearch = allowSearch;

        this.dataReader = dataReader;
        this.formatHandler = formatHandler;

        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    public void setGraphqlHandler(MixcloudGraphqlHandler graphqlHandler) {
        this.graphqlHandler = graphqlHandler;
    }

    @Override
    public String getSourceName() {
        return "mixcloud";
    }

    @Override
    public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
        if(graphqlHandler == null) return null;
        Matcher matcher;

        if((matcher = playlistPattern.matcher(reference.identifier)).find()) {
            return graphqlHandler.processPlaylist(matcher.group(2), matcher.group(1));
        }

        if((matcher = trackPattern.matcher(reference.identifier)).find()) {
            return graphqlHandler.processAsSigleTrack(matcher.group(2), matcher.group(1));
        }

        if((matcher = artistPattern.matcher(reference.identifier)).find()) {
            return graphqlHandler.processPlaylist(null, matcher.group(1));
        }

        if(allowSearch && reference.identifier.startsWith(SEARCH_PREFIX)) {
            return graphqlHandler.processSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
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
}
