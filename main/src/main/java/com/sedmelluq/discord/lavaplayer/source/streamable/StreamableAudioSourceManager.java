package com.sedmelluq.discord.lavaplayer.source.streamable;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
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
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class StreamableAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String STREAMABLE_URL = "https://streamable.com/";
    private static final String STREAMABLE_REGEX = "^(?:http://|https://|)(?:www\\.|m\\.|)streamable\\.com/(?:e/|)([a-zA-Z0-9-_]+)";
    private static final String VIDEO_DATA_REGEX = "var videoObject\\s*=\\s*(.*);";

    private static final Pattern streamablePattern = Pattern.compile(STREAMABLE_REGEX);
    private static final Pattern videoDataPattern = Pattern.compile(VIDEO_DATA_REGEX);

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
            return extractVideoFromPage(STREAMABLE_URL + m.group(1));
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

    private AudioTrack extractVideoFromPage(String url) {
        try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(url))) {
            HttpClientTools.assertSuccessWithContent(response, "track page info");

            String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            Matcher m = videoDataPattern.matcher(html);
            if (!m.find()) {
                throw new IOException("Video data not present on track page");
            }
            JsonBrowser json = JsonBrowser.parse(m.group(1));

            String artwork = json.get("thumbnail_url").isNull()
            ? json.get("poster_url").text()
            : json.get("thumbnail_url").text();

            AudioTrackInfo trackInfo = new AudioTrackInfo(
                json.get("title").safeText(),
                "Unknown author",
                (long) (json.get("duration").as(Double.class) * 1000.0),
                url,
                false,
                url,
                artwork != null ? "https:" + artwork : null
            );

            return new StreamableAudioTrack(trackInfo, this);
        } catch (IOException e) {
            throw new FriendlyException("Failed to load info for Streamable track", SUSPICIOUS, e);
        }
    }
}
