package com.sedmelluq.lavaplayer.source.streamable;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.ThreadLocalHttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class StreamableAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    public static final String STREAMABLE_URL = "https://streamable.com/";
    public static final String API_URL = "https://api.streamable.com/videos/";

    private static final String STREAMABLE_REGEX = "^(?:http://|https://|)(?:www\\.|m\\.|)streamable\\.com/(?:e/|)([a-zA-Z0-9-_]+)";

    private static final Pattern streamablePattern = Pattern.compile(STREAMABLE_REGEX);

    private final HttpInterfaceManager httpInterfaceManager;

    public StreamableAudioSourceManager() {
        httpInterfaceManager = new ThreadLocalHttpInterfaceManager(
            HttpClientTools
                .createSharedCookiesHttpBuilder()
                .setRedirectStrategy(new HttpClientTools.NoRedirectsStrategy()),
            HttpClientTools.DEFAULT_REQUEST_CONFIG
        );
    }

    @Override
    public String getSourceName() {
        return "streamable";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference) {
        Matcher m = streamablePattern.matcher(reference.identifier);

        if (m.find()) {
            return extractVideo(m.group(1));
        }

        return null;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) {
        // No custom values that need saving
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
        return new StreamableAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        // Nothing to shut down
    }

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

    private AudioTrack extractVideo(String id) {
        try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(API_URL + id))) {
            HttpClientTools.assertSuccessWithContent(response, "track info");

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

            String artworkUrl = json.get("thumbnail_url").text();

            if (artworkUrl != null && !artworkUrl.isBlank() && !artworkUrl.startsWith("https:"))
                artworkUrl = "https:" + artworkUrl;

            AudioTrackInfo trackInfo = new AudioTrackInfo(
                json.get("title").text(),
                "Unknown author",
                (long) (json.get("files").get("original").get("duration").as(Double.class) * 1000.0),
                id,
                false,
                STREAMABLE_URL + id,
                artworkUrl
            );

            return new StreamableAudioTrack(trackInfo, this);
        } catch (IOException e) {
            throw new FriendlyException("Failed to load info for Streamable track", SUSPICIOUS, e);
        }
    }
}
