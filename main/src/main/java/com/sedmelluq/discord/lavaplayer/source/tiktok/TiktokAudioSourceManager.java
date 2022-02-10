package com.sedmelluq.discord.lavaplayer.source.tiktok;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
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
 * Audio source manager which detects Tiktok tracks by URL.
 */
public class TiktokAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private final static String VIDEO_URL_REGEX = "^(?:http://|https://|)(?:www\\.|m\\.|)tiktok\\.com/@[^/]+/video/(\\d+)";
    private static final String MOBILE_URL_REGEX = "^(?:https?://)?vm\\.tiktok\\.com/\\w+";

    private final static Pattern videoUrlPattern = Pattern.compile(VIDEO_URL_REGEX);
    private final static Pattern mobileUrlPattern = Pattern.compile(MOBILE_URL_REGEX);

    private final static String API_URL = "http://api2.musical.ly/aweme/v1/aweme/detail/?aweme_id=";
    private final static String POST_URL = "https://www.tiktok.com/%s/video/%s";

    private final HttpInterfaceManager httpInterfaceManager;

    /**
     * Create an instance.
     */
    public TiktokAudioSourceManager() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "tiktok";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String url = reference.identifier;
        if (mobileUrlPattern.matcher(url).find()) {
            url = this.getVideoUrl(reference.identifier);
        }
        
        Matcher matcher = videoUrlPattern.matcher(url);
        if (matcher.find()) {
            JsonBrowser json = loadFromApi(matcher.group(1));
            if (json != null) {
                return buildTrack(json);
            }
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
        return new TiktokAudioTrack(trackInfo, this);
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

    public JsonBrowser loadFromApi(String id) {
        URI uri = URI.create(API_URL + id);
        try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(uri))) {
            HttpClientTools.assertSuccessWithContent(response, "TikTok api");

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            return json;
        } catch(IOException e) {
            throw new FriendlyException("Failed to fetch TikTok video information", SUSPICIOUS, e);
        }
    }

    private AudioTrack buildTrack(JsonBrowser json) {
        JsonBrowser details = json.get("aweme_detail");
        JsonBrowser video = details.get("video");

        String author = "@" + details.get("author").get("unique_id").safeText();
        String identifier = details.get("aweme_id").safeText();

        AudioTrackInfo info = new AudioTrackInfo(
            details.get("desc").text(),
            author,
            video.get("duration").asLong(Units.DURATION_MS_UNKNOWN),
            identifier,
            false,
            String.format(POST_URL, author, identifier),
            video.get("cover").get("url_list").index(0).text()
        );

        return new TiktokAudioTrack(info, this);
    }

    private String getVideoUrl(String url) {
        try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(url))) {
            return HttpClientTools.getRedirectLocation(url, response);
        } catch (IOException e) {
            throw new FriendlyException("Failed to get video url from mobile tiktok url", SUSPICIOUS, e);
        }
    }
}