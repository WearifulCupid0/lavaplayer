package com.sedmelluq.discord.lavaplayer.source.smule;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class SmuleAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final Pattern urlRegex = Pattern.compile("^(?:http://|https://|)(?:www\\.|)smule\\.com/recording/(?:[a-zA-Z0-9-_]+)/([0-9-_]+)");

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
    public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
        String id = getIdentifier(reference.identifier);
        if(id != null) {
            JsonBrowser metadata = getMetadata(id);
            return buildTrack(metadata);
        }

        return null;
    }

    private String getIdentifier(String url) {
        Matcher matcher = urlRegex.matcher(url);
        if(matcher.find()) return matcher.group(1);
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
        return new SmuleAudioTrack(trackInfo, this);
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

    private JsonBrowser getMetadata(String identifier) {
        try(CloseableHttpResponse response = getHttpInterface().execute(new HttpGet("https://www.smule.com/p/" + identifier + "/json"))) {
            HttpClientTools.assertSuccessWithContent(response, "track metadata");

            JsonBrowser metadata = JsonBrowser.parse(response.getEntity().getContent());

            return metadata;
        } catch(IOException e) {
            throw new FriendlyException("Failed to load Smule metadata", SUSPICIOUS, e);
        }
    }

    private AudioTrack buildTrack(JsonBrowser data) {
        if(!data.isNull() && !data.get("media_url").isNull()) {
            String title = data.get("title").text();
            String author = data.get("owner").get("handle").text();
            if(author == null) author = data.get("artist").text();
            String artwork = data.get("cover_url").text();
            if(artwork == null) artwork = data.get("owner").get("pic_url").text();
            String uri = "https://www.smule.com" + data.get("web_url").safeText();
            return new SmuleAudioTrack(new AudioTrackInfo(title, author, (long) (data.get("song_length").as(Double.class) * 1000.0), uri, false, uri, artwork), this);
        }
        return null;
    }
}
