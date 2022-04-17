package com.sedmelluq.discord.lavaplayer.source.audiomack;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
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
import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class AudiomackAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String TRACK_URL = "https://www.audiomack.com/%s/song/%s";
    private static final String API_URL = "https://www.audiomack.com/api/music/url/song/%s?extended=1&_=%s";
    private static final String REGEX = "^(?:https://|http://|)(?:www\\.|)audiomack\\.com/(.*)/song/(.*)";

    private static final Pattern pattern = Pattern.compile(REGEX);

    private final HttpInterfaceManager httpInterfaceManager;

    public AudiomackAudioSourceManager() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "audiomack";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference) {
        Matcher matcher = pattern.matcher(reference.identifier);

        if (matcher.find()) return extractTrackData(matcher.group(1), matcher.group(2));

        return null;
    }

    public String[] parseURL(String input) {
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            String[] result = new String[2];
            result[0] = matcher.group(1);
            result[1] = matcher.group(2);
            return result;
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
        return new AudiomackAudioTrack(trackInfo, this);
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

    private AudioTrack extractTrackData(String author, String title) {
        String slug = author + "/" + title;
        try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(URI.create(String.format(API_URL, slug, slug))))) {
            HttpClientTools.assertSuccessWithContent(response, "api response");

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            String uri = String.format(TRACK_URL, author, title);
            AudioTrackInfo trackInfo = new AudioTrackInfo(
                json.get("title").text(),
                json.get("uploader").isNull() ? json.get("artist").text() : json.get("uploader").text(),
                (long) (json.get("duration").as(Double.class) * 1000.0),
                uri,
                false,
                uri
            );

            return new AudiomackAudioTrack(trackInfo, this);
        } catch (IOException e) {
            throw new FriendlyException("Failed to load Audiomack track info.", SUSPICIOUS, e);
        }
    }
}
