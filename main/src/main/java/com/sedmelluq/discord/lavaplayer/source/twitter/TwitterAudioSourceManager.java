package com.sedmelluq.discord.lavaplayer.source.twitter;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.*;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class TwitterAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private final static String TWITTER_URL = "https://twitter.com/%s/status/%s";
    private final static String TWITTER_REGEX = "^(?:http://|https://|)(?:www\\.|m\\.|mobile\\.|)twitter\\.com/[^/]+/(?:status|cards/tfw/v1|videos(?:/tweet|))/(\\d+)";

    private final static Pattern twitterPattern = Pattern.compile(TWITTER_REGEX);

    private final HttpInterfaceManager httpInterfaceManager;

    public TwitterAudioSourceManager() {
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
        this.httpInterfaceManager.setHttpContextFilter(new TwitterHttpContextFilter(httpInterfaceManager));
    }
    
    @Override
    public String getSourceName() {
      return "twitter";
    }
  
    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        Matcher matcher = twitterPattern.matcher(reference.identifier);

        if (matcher.find()) {
            JsonBrowser json = fetchJsonFromApi(matcher.group(1));
            return extractTrackFromJson(json);
        }

        return null;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        // nothing to encode
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new TwitterAudioTrack(trackInfo, this);
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

    private JsonBrowser fetchJsonFromApi(String id) {
        try (
            HttpInterface httpInterface = getHttpInterface();
            CloseableHttpResponse response = httpInterface.execute(new HttpGet(String.format(TwitterConstants.STATUS_URL, id)))
        ) {
            HttpClientTools.assertSuccessWithContent(response, "post data");
            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

            return json;
        } catch (IOException e) {
            throw new FriendlyException("Failed to fetch Twitter post.", SUSPICIOUS, e);
        }
    }

    private AudioTrack extractTrackFromJson(JsonBrowser json) {
        JsonBrowser media = json.get("extended_entities").get("media").index(0);
        JsonBrowser videoInfo = media.get("video_info");

        if (media.isNull() || videoInfo.isNull()) {
            throw new FriendlyException("This post doesn't contain a video.", COMMON, null);
        }

        String title = json.get("full_text").text();
        if (title == null || title.isEmpty()) title = "Twitter post.";

        String author = json.get("user").get("screen_name").safeText();
        long length = videoInfo.get("duration_millis").asLong(Units.DURATION_MS_UNKNOWN);
        List<JsonBrowser> variants = videoInfo.get("variants").values();
        JsonBrowser bestFormat = null;

        for (JsonBrowser format : variants) {
            if (!format.get("content_type").safeText().equals("video/mp4")) break;
            if (bestFormat == null) {
                bestFormat = format;
            } else if (format.get("bitrate").as(int.class) > bestFormat.get("bitrate").as(int.class)) {
                bestFormat = format;
            }
        }
        if (bestFormat == null || bestFormat.isNull()) {
            throw new FriendlyException("Failed to get a valid format for Twitter post.", SUSPICIOUS, null);
        }

        String identifier = bestFormat.get("url").text();

        AudioTrackInfo trackInfo = new AudioTrackInfo(
            title,
            author,
            length,
            identifier,
            false,
            String.format(TWITTER_URL, author, json.get("id").text()),
            media.get("media_url_https").text()
        );

        return new TwitterAudioTrack(trackInfo, this);
    }
}
