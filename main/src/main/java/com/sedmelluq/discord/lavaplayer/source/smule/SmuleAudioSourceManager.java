package com.sedmelluq.discord.lavaplayer.source.smule;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
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
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class SmuleAudioSourceManager implements HttpConfigurable, AudioSourceManager {
    private final static String REGEX = "(?:http://|https://(?:www\\.)?)?smule\\.com/(?:.*)/([a-zA-Z0-9-_]+)/([0-9-_]+)";
    private final static Pattern pattern = Pattern.compile(REGEX);

    private final HttpInterfaceManager httpInterfaceManager;

    public SmuleAudioSourceManager() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "smule";
    }

    @Override
    public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
        Matcher matcher = pattern.matcher(reference.identifier);

        if(matcher.find()) {
            String slug = matcher.group(1);
            String id = matcher.group(2);
            return buildTrack(slug, getTrackData(id));
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
        return new SmuleAudioTrack(trackInfo, this);
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

    private JsonBrowser getTrackData(String id) {
        try (HttpInterface httpInterface = getHttpInterface()) {
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://www.smule.com/p/" + id + "/json"))) {
                HttpClientTools.assertSuccessWithContent(response, "track response");
                JsonBrowser data = JsonBrowser.parse(response.getEntity().getContent());
                return data;
            }
        } catch (IOException e) {
            throw new FriendlyException("Failed to load info for smule track", SUSPICIOUS, null);
        }
    }

    private AudioTrack buildTrack(String slug, JsonBrowser json) {
        String identifier = "https://www.smule.com/recording/" + slug + "/" + json.get("key").text();
        String author = json.get("owner").get("handle").isNull()
        ? json.get("artist").text()
        : json.get("owner").get("handle").text();
        AudioTrackInfo trackInfo = new AudioTrackInfo(
            json.get("title").text(),
            author,
            (long) (json.get("song_length").as(Double.class) * 1000.0),
            identifier,
            false,
            identifier,
            json.get("cover_url").text()
        );
        return new SmuleAudioTrack(trackInfo, this);
    }
}
