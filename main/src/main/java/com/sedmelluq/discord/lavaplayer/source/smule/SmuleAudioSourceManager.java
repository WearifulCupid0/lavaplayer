package com.sedmelluq.discord.lavaplayer.source.smule;

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

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio source manager which detects Smule tracks by URL.
 */
public class SmuleAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private final static String URL_REGEX = "^(?:http://|https://|)(?:www\\.|)smule\\.com/recording/(?:[a-zA-Z0-9-_]+)/([0-9-_]+)";

    private final static Pattern urlPattern = Pattern.compile(URL_REGEX);

    private final static String API_URL = "https://www.smule.com/p/%s/json";
    private final static String SMULE_URL = "https://www.smule.com";

    private final HttpInterfaceManager httpInterfaceManager;

    /**
     * Create an instance.
     */
    public SmuleAudioSourceManager() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "smule";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        Matcher matcher = urlPattern.matcher(reference.identifier);
        
        if (matcher.find()) {
            return loadTrack(matcher.group(1));
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
        return new SmuleAudioTrack(trackInfo, this);
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

    public AudioTrack loadTrack(String id) {
        URI uri = URI.create(String.format(API_URL, id));
        try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(uri))) {
            HttpClientTools.assertSuccessWithContent(response, "smule track");

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            
            String identifier = SMULE_URL + json.get("web_url").safeText();

            AudioTrackInfo trackInfo = new AudioTrackInfo(
                json.get("title").safeText(),
                json.get("owner").get("handle").isNull() ? json.get("artist").safeText() : json.get("owner").get("handle").safeText(),
                (long) (json.get("song_length").as(Double.class) * 1000.0),
                identifier,
                false,
                identifier,
                json.get("cover_url").text()
            );

            return new SmuleAudioTrack(trackInfo, this);
        } catch(IOException e) {
            throw new FriendlyException("Failed to fetch Smule track information", SUSPICIOUS, e);
        }
    }
}