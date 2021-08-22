package com.sedmelluq.discord.lavaplayer.source.dailymotion;

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
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;

public class DailyMotionAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String BASE_REGEX = "(?:http://|https://|)(?:www\\.|)dailymotion\\.com/(?:embed/)";
    private static final String VIDEO_REGEX = BASE_REGEX + "video/([a-zA-Z0-9-_]+)";
    private static final String PLAYLIST_REGEX = BASE_REGEX + "playlist/([a-zA-Z0-9-_]+)";
    private static final String CHANNEL_REGEX = "(?:http://|https://|)(?:www\\.|)dailymotion\\.com/([a-zA-Z0-9-_]+)";

    private static final String SEARCH_REGEX = "dysearch:";
    private static final String SIMILAR_REGEX = "dysimilar:";

    private static final Pattern videoPattern = Pattern.compile(VIDEO_REGEX);
    private static final Pattern playlistPattern = Pattern.compile(PLAYLIST_REGEX);
    private static final Pattern channelPattern = Pattern.compile(CHANNEL_REGEX);

    private final boolean allowSearch;
    private DailyMotionGraphqlHandler graphqlHandler = null;

    private final DailyMotionAuthorizationManager authorizationManager = new DailyMotionAuthorizationManager();

    private final HttpInterfaceManager httpInterfaceManager;

    /**
    * Create an instance.
    */
    public DailyMotionAudioSourceManager() {
        this(true);
    }

    public DailyMotionAudioSourceManager(boolean allowSearch) {
        this.allowSearch = allowSearch;

        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
        httpInterfaceManager.setHttpContextFilter(new DailyMotionHttpContextFilter(this));

        this.setGraphqlHandler(new DefaultDailyMotionGraphqlHandler(httpInterfaceManager));
    }

    public void setGraphqlHandler(DailyMotionGraphqlHandler graphqlHandler) {
        this.graphqlHandler = graphqlHandler;
    }

    @Override
    public String getSourceName() {
        return "dailymotion";
    }

    @Override
    public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
        Matcher matcher;

        if((matcher = videoPattern.matcher(reference.identifier)).find()) {
            return graphqlHandler.video(matcher.group(1), this::getTrack);
        }
        if((matcher = playlistPattern.matcher(reference.identifier)).find()) {
            return graphqlHandler.playlist(matcher.group(1), this::getTrack);
        }
        if((matcher = channelPattern.matcher(reference.identifier)).find()) {
            return graphqlHandler.channel(matcher.group(1), this::getTrack);
        }
        if(reference.identifier.startsWith(SIMILAR_REGEX)) {
            return graphqlHandler.similar(reference.identifier.substring(SIMILAR_REGEX.length()).trim(), this::getTrack);
        }
        if(allowSearch && reference.identifier.startsWith(SEARCH_REGEX)) {
            return graphqlHandler.search(reference.identifier.substring(SEARCH_REGEX.length()).trim(), this::getTrack);
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
        return new DailyMotionAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager);
    }

    public DailyMotionAudioTrack getTrack(AudioTrackInfo info) {
        return new DailyMotionAudioTrack(info, this);
    }

    public DailyMotionAuthorizationManager getAuthorizationManager() {
        return authorizationManager;
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
