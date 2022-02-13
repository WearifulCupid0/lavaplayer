package com.sedmelluq.discord.lavaplayer.source.instagram;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio source manager which detects Instagram tracks by URL.
 */
public class InstagramAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private final static String INSTAGRAM_URL = "https://instagram.com/p/";
    private final static String POST_REGEX = "^(?:http://|https://|)(?:www\\.|)instagram\\.com/p/([a-zA-Z0-9-_]+)";
    private final static Pattern postPattern = Pattern.compile(POST_REGEX);

    private final HttpInterfaceManager httpInterfaceManager;

    /**
     * Create an instance.
     */
    public InstagramAudioSourceManager() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "instagram";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        Matcher matcher = postPattern.matcher(reference.identifier);

        if (matcher.find()) {
            JsonBrowser json = loadFromApi(INSTAGRAM_URL + matcher.group(1));
            JsonBrowser data = json.get("graphql").get("shortcode_media");
            if (!data.get("has_audio").asBoolean(true)) {
                throw new FriendlyException("Instagram post doesn't contain an audio", COMMON, null);
            }
            return buildTrack(data);
        }

        return null;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) {
        // Nothing special to encode
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new InstagramAudioTrack(trackInfo, this);
    }

    @Override
     public void shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager);
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
    }

    /**
     * @return Get an HTTP interface for a playing track.
     */
    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    public JsonBrowser loadFromApi(String url) {
        URI uri = URI.create(url + "?__a=1");
        try(CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(uri))) {
            HttpClientTools.assertSuccessWithContent(response, "Instagram api");

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            return json;
        } catch(IOException e) {
            throw new FriendlyException("Failed to fetch Instagram video information", SUSPICIOUS, e);
        }
    }

    private AudioTrack buildTrack(JsonBrowser json) {
        String identifier = json.get("shortcode").text();

        AudioTrackInfo info = new AudioTrackInfo(
            json.get("title").isNull() ? json.get("owner").get("full_name").text() + " Post" : json.get("title").text(),
            json.get("owner").get("username").text(),
            (long) (json.get("video_duration").as(Double.class) * 1000.0),
            identifier,
            false,
            String.format("https://www.instagram.com/p/%s/", identifier),
            json.get("thumbnail_src").text()
        );

        return new InstagramAudioTrack(info, this);
    }
}