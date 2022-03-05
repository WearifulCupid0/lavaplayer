package com.sedmelluq.discord.lavaplayer.source.newgrounds;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
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
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;
import static com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN;

/**
 * Audio source manager which detects NewGrounds tracks by URL.
 */
public class NewgroundsAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String URL_REGEX = "^https://www.newgrounds.com/(portal/view|audio/listen)/([0-9]+)(?:\\?.*|)$";
    private static final String HTML_IMAGE_REGEX = "\"background-image: url\\('(.*)'\\)\"";

    private static final Pattern urlPattern = Pattern.compile(URL_REGEX);
    private static final Pattern imageHtmlPattern = Pattern.compile(HTML_IMAGE_REGEX);

    private final HttpInterfaceManager httpInterfaceManager;

    /**
     * Create an instance.
     */
    public NewgroundsAudioSourceManager() {
        httpInterfaceManager = new ThreadLocalHttpInterfaceManager(
            HttpClientTools
                .createSharedCookiesHttpBuilder()
                .setRedirectStrategy(new HttpClientTools.NoRedirectsStrategy()),
            HttpClientTools.DEFAULT_REQUEST_CONFIG
        );
    }

    @Override
    public String getSourceName() {
        return "newgrounds";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        Matcher matcher = urlPattern.matcher(reference.identifier);
        if (!matcher.matches()) {
            return null;
        }
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            switch (matcher.group(1)) {
                case "portal/view":
                    return loadAudioItemVideoId(httpInterface, matcher.group(2), reference.identifier);
                case "audio/listen":
                    return loadAudioItemAudioId(httpInterface, matcher.group(2), reference.identifier);
            }
        } catch (IOException e) {
            throw new FriendlyException("Loading NewGrounds track information failed.", SUSPICIOUS, e);
        }
        return null;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        // Nothing special to encode
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new NewgroundsAudioTrack(trackInfo, this);
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

    private AudioItem loadAudioItemVideoId(HttpInterface httpInterface, String videoId, String videoUrl) throws IOException {
        JsonBrowser json = loadJsonFromUrl(httpInterface, "https://www.newgrounds.com/portal/video/" + videoId);
        if (json == null) {
            return AudioReference.NO_TRACK;
        }

        JsonBrowser error = json.get("error");
        if (!error.isNull()) {
            int statusCode = error.get("code").as(Integer.class);
            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                return AudioReference.NO_TRACK;
            }
            throw new FriendlyException("Server responded with an error.", SUSPICIOUS, new IllegalStateException("Response code is " + statusCode + ". Message is " + error.get("msg").text()));
        }

        List<JsonBrowser> sources = json.get("sources").values();

        return new NewgroundsAudioTrack(new AudioTrackInfo(
                json.get("title").text(),
                json.get("author").text(),
                DURATION_MS_UNKNOWN,
                sources.get(sources.size() - 1).get("src").text(),
                false,
                videoUrl
        ), this);
    }

    private AudioItem loadAudioItemAudioId(HttpInterface httpInterface, String audioId, String audioUrl) throws IOException {
        JsonBrowser json = loadJsonFromUrl(httpInterface, "https://www.newgrounds.com/audio/load/" + audioId);
        if (json == null) {
            return AudioReference.NO_TRACK;
        }

        String artwork = null;
        String html = json.get("html").text();
        if (html != null && !html.isEmpty()) {
            Matcher m = imageHtmlPattern.matcher(html);
            if (m.find()) artwork = m.group(1);
        }

        return new NewgroundsAudioTrack(new AudioTrackInfo(
                json.get("title").text(),
                json.get("author").text(),
                json.get("duration").as(Long.class) * 1000,
                json.get("sources").index(0).get("src").text(),
                false,
                audioUrl,
                artwork
        ), this);
    }

    public JsonBrowser loadJsonFromUrl(HttpInterface httpInterface, String url) throws IOException, FriendlyException {
        HttpUriRequest request = new HttpGet(url);
        request.setHeader("X-Requested-With", "XMLHttpRequest");
        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                return null;
            }
            if (statusCode != HttpStatus.SC_OK) {
                throw new FriendlyException("Server responded with an error.", SUSPICIOUS, new IllegalStateException("Response code is " + statusCode));
            }
            return JsonBrowser.parse(response.getEntity().getContent());
        }
    }
}