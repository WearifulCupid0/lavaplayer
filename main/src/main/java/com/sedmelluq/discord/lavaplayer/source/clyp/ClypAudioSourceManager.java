package com.sedmelluq.discord.lavaplayer.source.clyp;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.PBJUtils;
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

/**
 * Audio source manager which detects Clyp tracks by URL.
 */
public class ClypAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String URL_REGEX = "^(?:http://|https://|)(?:www\\.|api\\.|audio\\.|)clyp\\.it/([a-zA-Z0-9-_]+)";
    private static final String API_URL = "https://api.clyp.it/%s/playlist";

    private static final Pattern urlPattern = Pattern.compile(URL_REGEX);

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
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String id = getIdentifier(reference.identifier);
        if (id != null) {
            JsonBrowser metadata = getMetadata(id);
            return buildTrack(metadata);
        }

        return null;
    }

    public String getIdentifier(String url) {
        Matcher matcher = urlPattern.matcher(url);
        if (matcher.find()) return matcher.group(1);
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
        URI uri = URI.create(String.format(API_URL, identifier));
        try(CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(uri))) {
            HttpClientTools.assertSuccessWithContent(response, "audio api response");
            
            return JsonBrowser.parse(response.getEntity().getContent());
        } catch(IOException e) {
            throw new FriendlyException("Failed to fetch Clyp song information", SUSPICIOUS, e);
        }
    }

    private AudioItem buildTrack(JsonBrowser data) {
        if (!data.get("AudioFiles").isNull() && data.get("AudioFiles").isList() && !data.get("AudioFiles").index(0).isNull()) {
            JsonBrowser audioFile = data.get("AudioFiles").index(0);
            String title = audioFile.get("Title").text();
            String identifier = "https://clyp.it/" + audioFile.get("AudioFileId").text();
            String author = audioFile.get("User").get("LastName").isNull()
            ? audioFile.get("User").get("FirstName").text()
            : audioFile.get("User").get("FirstName").text() + " " + audioFile.get("User").get("LastName").text();
            
            return new ClypAudioTrack(new AudioTrackInfo(
                title,
                author,
                (long) (audioFile.get("Duration").as(Double.class) * 1000.0),
                identifier,
                false,
                identifier,
                PBJUtils.getClypArtwork(audioFile)
            ), this);
        }

        return AudioReference.NO_TRACK;
    }
}