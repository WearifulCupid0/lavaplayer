package com.sedmelluq.discord.lavaplayer.source.rumble;

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
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class RumbleAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final Logger log = LoggerFactory.getLogger(RumbleAudioSourceManager.class);

    private static final String TRACK_URL_REGEX = "^https?://(?:www\\.)?rumble\\.com/v\\w{6}.+";
    private static final String EMBED_TRACK_URL_REGEX = "^https?://(?:www\\.)?rumble\\.com/embed/(?<id>v\\w{5,6})";
    private static final String SCRIPT_REGEX = "<script type=application/ld\\+json>(.+)</script>";
    private static final String AUTHOR_REGEX = "<span class=\"media-heading-name\">(.+)(?:</span>|<svg)";

    private static final Pattern trackUrlPattern = Pattern.compile(TRACK_URL_REGEX);
    private static final Pattern embedTrackUrlPattern = Pattern.compile(EMBED_TRACK_URL_REGEX);
    private static final Pattern scriptPattern = Pattern.compile(SCRIPT_REGEX);
    private static final Pattern authorPattern = Pattern.compile(AUTHOR_REGEX);

    private final HttpInterfaceManager httpInterfaceManager;

    public RumbleAudioSourceManager() {
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "rumble";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        final Matcher matcher = trackUrlPattern.matcher(reference.identifier);

        if (matcher.matches()) {
            return loadTrack(reference.identifier);
        }

        return null;
    }

    private AudioItem loadTrack(String url) {
        try (final HttpInterface httpInterface = getHttpInterface()) {
            log.debug("Loading Rumble track info from url: {}", url);

            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(url))) {
                int statusCode = response.getStatusLine().getStatusCode();

                if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                    throw new IOException("Unexpected response code from video info: " + statusCode);
                }

                final String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                final Matcher scriptMatcher = scriptPattern.matcher(html);

                if (!scriptMatcher.find()) {
                    throw new IOException("Could not extract the track script");
                }

                final JsonBrowser json = JsonBrowser.parse(scriptMatcher.group(1));
                final JsonBrowser info = json.index(0);

                final String embedUrl = info.get("embedUrl").text();
                final Matcher idMatcher = embedTrackUrlPattern.matcher(embedUrl);

                if (!idMatcher.find()) {
                    throw new IOException("Could not get extract track id");
                }

                final Matcher authorMatcher = authorPattern.matcher(html);

                if (!authorMatcher.find()) {
                    throw new IOException("Could not get the track author");
                }

                return new RumbleAudioTrack(new AudioTrackInfo(
                        info.get("name").safeText(),
                        authorMatcher.group(1).trim(),
                        parseDuration(info.get("duration").safeText()),
                        idMatcher.group("id"),
                        false,
                        info.get("url").safeText(),
                        info.get("thumbnailUrl").text()
                ), this);
            }
        } catch (Exception e) {
            throw new FriendlyException("Error occurred when extracting video info", SUSPICIOUS, e);
        }
    }

    private long parseDuration(String duration) {
        long durationMs = 0L;

        // Example duration: PT00H01M41S
        try {
            durationMs += Long.parseLong(duration.substring(2, 4)) * 60 * 60 * 1000;
            durationMs += Long.parseLong(duration.substring(5, 7)) * 60 * 1000;
            durationMs += Long.parseLong(duration.substring(8, 10)) * 1000;
        } catch (NumberFormatException ex) {
            throw new FriendlyException("Failed to parse Rumble track duration.", SUSPICIOUS, ex);
        }

        return durationMs;
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

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
    }
}