package com.sedmelluq.discord.lavaplayer.source.clyp;

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

public class ClypAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final Pattern urlRegex = Pattern.compile("^(?:http://|https://|)clyp\\.it/([a-zA-Z0-9-_]+)$");

    private final HttpInterfaceManager httpInterfaceManager;

    /**
    * Create an instance.
    */
    public ClypAudioSourceManager() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "clyp";
    }

    @Override
    public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
        Matcher matcher = urlRegex.matcher(reference.identifier);
        if(matcher.matches()) {
            JsonBrowser metadata = getMetadata(matcher.group(1));
            return parseMetadata(metadata);
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
        return new ClypAudioTrack(trackInfo, this);
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
        try(CloseableHttpResponse response = getHttpInterface().execute(new HttpGet("https://api.clyp.it/" + identifier + "/playlist"))) {
            HttpClientTools.assertSuccessWithContent(response, "audio metadata");

            JsonBrowser metadata = JsonBrowser.parse(response.getEntity().getContent());

            return metadata;
        } catch(IOException e) {
            throw new FriendlyException("Failed to load Clyp metadata", SUSPICIOUS, e);
        }
    }

    private AudioTrack parseMetadata(JsonBrowser data) {
        if(!data.get("AudioFiles").isNull() && data.get("AudioFiles").isList() && !data.get("AudioFiles").index(0).isNull()) {
            JsonBrowser audioFile = data.get("AudioFiles").index(0);
            String title = audioFile.get("Title").text();
            String identifier = audioFile.get("AudioFileId").text();
            String uri = audioFile.get("Url").text();
            String artwork = audioFile.get("ArtworkPictureUrl").isNull()
            ? audioFile.get("User").get("ProfilePictureUrl").text()
            : audioFile.get("ArtworkPictureUrl").text();
            String author = audioFile.get("User").get("LastName").isNull()
            ? audioFile.get("User").get("FirstName").text()
            : audioFile.get("User").get("FirstName").text() + " " + audioFile.get("User").get("LastName").text();
            
            return new ClypAudioTrack(new AudioTrackInfo(title, author, (long) (audioFile.get("Duration").as(Double.class) * 1000.0), identifier, false, uri, artwork), this);
        }

        return null;
    }
}
