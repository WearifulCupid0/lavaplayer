package com.sedmelluq.discord.lavaplayer.source.reverbnation;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
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
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class ReverbnationAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String REGEX = "^(?:http://|https://|)(?:www\\.|)reverbnation\\.com/(?:.*)/song/(\\d+)";
    private static final Pattern pattern = Pattern.compile(REGEX);

    private final HttpInterfaceManager httpInterfaceManager;

    /**
    * Create an instance.
    */
    public ReverbnationAudioSourceManager() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "reverbnation";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        Matcher matcher = pattern.matcher(reference.identifier);

        if (matcher.find()) return loadFromIdentifier(matcher.group(1));

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
        return new ReverbnationAudioTrack(trackInfo, this);
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

    private AudioTrack loadFromIdentifier(String id) {
        URI uri = URI.create("https://api.reverbnation.com/song/" + id);
        try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(uri))) {
            HttpClientTools.assertSuccessWithContent(response, "api response");

            String responseData = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseData);

            AudioTrackInfo trackInfo = new AudioTrackInfo(
                json.get("name").safeText(),
                json.get("artist").get("name").safeText(),
                (long) (json.get("duration").as(Double.class) * 1000.0),
                json.get("id").text(),
                false,
                json.get("share_url").text(),
                json.get("image").safeText().replace("resize:64x48", "resize:1000x1000")
            );

            return new ReverbnationAudioTrack(trackInfo, this);
        } catch (IOException e) {
            throw new FriendlyException("Failed to load Reverbnation track info", SUSPICIOUS, e);
        }
    }
}
