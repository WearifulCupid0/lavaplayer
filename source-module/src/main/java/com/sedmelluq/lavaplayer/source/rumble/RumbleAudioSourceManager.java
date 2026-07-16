package com.sedmelluq.lavaplayer.source.rumble;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class RumbleAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final Logger log = LoggerFactory.getLogger(RumbleAudioSourceManager.class);

    private static final String API_URL = "https://rumble.com/service.php";

    private static final String TRACK_URL_REGEX = "^(?:https?://)?(?:www\\.)?rumble\\.com/([^?#]+)(?:[?#].*)?$";

    private static final Pattern trackUrlPattern = Pattern.compile(TRACK_URL_REGEX, Pattern.CASE_INSENSITIVE);

    private final String SEARCH_PREFIX = "rmsearch:";

    private final boolean allowSearch;

    private final HttpInterfaceManager httpInterfaceManager;

    public RumbleAudioSourceManager() {
        this(true);
    }

    public RumbleAudioSourceManager(boolean allowSearch) {
        this.allowSearch = allowSearch;

        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "rumble";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if(allowSearch && reference.identifier.startsWith(SEARCH_PREFIX)) {
            return loadSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
        }

        Matcher matcher = trackUrlPattern.matcher(reference.identifier);
        if (matcher.matches()) {
            String url = matcher.group(1);
            if (url != null && !url.isBlank())
                return loadTrack(url);
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
        return new RumbleAudioTrack(trackInfo, this);
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

    public HttpInterfaceManager getInterfaceManager() { return httpInterfaceManager; }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
    }

    private AudioItem loadTrack(String url) {
        if (!url.startsWith("/"))
            url = "/" + url;

        try (HttpInterface httpInterface = getHttpInterface()) {
            URI uri = createTrackResolveUrl(url);
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "rumble track resolve api");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (
                        json.get("data").get("object_type").safeText().equals("video") &&
                        !json.get("data").get("videos").index(0).get("url").safeText().isBlank() &&
                        json.get("data").get("videos").index(0).get("type").safeText().equals("hls")
                ) return buildTrack(json.get("data"));

                return AudioReference.NO_TRACK;
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Rumble track url", SUSPICIOUS, e);
        }
    }

    private AudioItem loadSearch(String query) {
        try (HttpInterface httpInterface = getHttpInterface()) {
            URI uri = createSearchUrl(query);
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "rumble search api");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                JsonBrowser videos = json.get("data").get("video").get("items");

                if (videos.isNull() || !videos.isList())
                    return AudioReference.NO_TRACK;

                List<AudioTrack> tracks = new ArrayList<>();

                for (JsonBrowser trackData : videos.values()) {
                    if (
                            !trackData.get("videos").index(0).get("url").safeText().isBlank() &&
                            trackData.get("videos").index(0).get("type").safeText().equals("hls")
                    ) tracks.add(buildTrack(trackData));
                }

                if (tracks.isEmpty())
                    return AudioReference.NO_TRACK;

                return BasicAudioPlaylist.createSearchResults(query, tracks);
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Rumble track url", SUSPICIOUS, e);
        }
    }

    private AudioTrack buildTrack(JsonBrowser trackData) {
        AudioTrackAuthorInfo authorInfo = new AudioTrackAuthorInfo(
                trackData.get("by").get("name").text(),
                trackData.get("by").get("url").text()
        );

        boolean isLive = trackData.get("live").asBoolean(false);

        AudioTrackInfo trackInfo = new AudioTrackInfo(
                trackData.get("title").safeText(),
                authorInfo,
                isLive ? Units.DURATION_MS_UNKNOWN : (long) (trackData.get("duration").as(Double.class) * 1000.0),
                trackData.get("videos").index(0).get("url").text(),
                isLive,
                trackData.get("url").text(),
                trackData.get("thumb").text(),
                null
        );

        return new RumbleAudioTrack(trackInfo, this);
    }

    private URI createSearchUrl(String query) {
        try {
            return new URIBuilder(API_URL)
                    .addParameter("api", "7")
                    .addParameter("name", "search")
                    .addParameter("query", query)
                    .build();
        } catch (URISyntaxException e) {
            log.error("Failed to create Rumble search api url.", e);
            return null;
        }
    }

    private URI createTrackResolveUrl(String url) {
        try {
            return new URIBuilder(API_URL)
                    .addParameter("api", "7")
                    .addParameter("name", "media.details")
                    .addParameter("url", url)
                    .build();
        } catch (URISyntaxException e) {
            log.error("Failed to create Rumble track resolve api url.", e);
            return null;
        }
    }
}