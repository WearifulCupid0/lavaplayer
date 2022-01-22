package com.sedmelluq.discord.lavaplayer.source.bandlab;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
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

public class BandlabAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String ALBUM_REGEX = "^(?:http://|https://|)www\\.bandlab\\.com/([a-zA-Z0-9-_]+)/albums/([a-zA-Z0-9-_]+)/?(?:\\?.*|)$";
    private static final String COLLECTION_REGEX = "^(?:http://|https://|)www\\.bandlab\\.com/([a-zA-Z0-9-_]+)/collections/([a-zA-Z0-9-_]+)/?(?:\\?.*|)$";
    private static final String TRACK_REGEX = "^(?:http://|https://|)www\\.bandlab\\.com/([a-zA-Z0-9-_]+)/([a-zA-Z0-9-_]+)/?(?:\\?.*|)$";

    private static final Pattern albumPattern = Pattern.compile(ALBUM_REGEX);
    private static final Pattern collectionPattern = Pattern.compile(COLLECTION_REGEX);
    private static final Pattern trackPattern = Pattern.compile(TRACK_REGEX);

    private final BandlabDataLoader dataLoader;
    private final HttpInterfaceManager httpInterfaceManager;

    /**
    * Create an instance.
    */
    public BandlabAudioSourceManager() {
        this(new DefaultBandlabDataLoader());
    }

    public BandlabAudioSourceManager(
        BandlabDataLoader dataLoader) {
        this.dataLoader = dataLoader;
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "bandlab";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        Matcher matcher;
        if (( matcher = albumPattern.matcher(reference.identifier) ).find()) {
            return dataLoader.loadAlbum(matcher.group(2), this::getTrack);
        }
        if (( matcher = collectionPattern.matcher(reference.identifier) ).find()) {
            return dataLoader.loadCollection(matcher.group(2), this::getTrack);
        }
        if (( matcher = trackPattern.matcher(reference.identifier) ).find()) {
            return dataLoader.loadTrack(matcher.group(1), matcher.group(2), this::getTrack);
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
        return new BandlabAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager);
        this.dataLoader.shutdown();
    }

    public AudioTrack getTrack(AudioTrackInfo trackInfo) {
        return new BandlabAudioTrack(trackInfo, this);
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