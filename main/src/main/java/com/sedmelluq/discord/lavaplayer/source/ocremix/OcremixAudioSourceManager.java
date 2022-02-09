package com.sedmelluq.discord.lavaplayer.source.ocremix;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.ThreadLocalHttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class OcremixAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String OCREMIX_MAIN_URL = "https://ocremix.org";
    private static final String OCREMIX_URL = OCREMIX_MAIN_URL + "/remix/%s";

    private static final String OCREMIX_REGEX = "(?:https?://(?:www\\.)?ocremix\\.org/remix/)?(?<id>OCR[\\d]+)(?:.*)?";

    private static final Pattern ocremixPattern = Pattern.compile(OCREMIX_REGEX);

    private final HttpInterfaceManager httpInterfaceManager;

    public OcremixAudioSourceManager() {
        httpInterfaceManager = new ThreadLocalHttpInterfaceManager(
            HttpClientTools
                .createSharedCookiesHttpBuilder()
                .setRedirectStrategy(new HttpClientTools.NoRedirectsStrategy()),
            HttpClientTools.DEFAULT_REQUEST_CONFIG
        );
    }

    @Override
    public String getSourceName() {
        return "ocremix";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference) {
        Matcher m = ocremixPattern.matcher(reference.identifier);

        if (m.find()) {
            return extractTrackFromIdentifier(m.group("id"));
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
        return new OcremixAudioTrack(trackInfo, this);
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

    private AudioTrack extractTrackFromIdentifier(String id) {
        String url = OCREMIX_URL + id;
        try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(String.format(url + "?view=xml")))) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("Unexpected status code from Overcloked Remix track page: " + statusCode);
            }

            String xml = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            Document document = Jsoup.parse(xml);
            Element remix = document.selectFirst("remix");

            if (remix == null) {
                throw new IOException("Track details wasn't present on track page.");
            }

            AudioTrackInfo trackInfo = new AudioTrackInfo(
                remix.attr("name"),
                document.selectFirst("remixers")
                .children()
                .stream()
                .map((remixer) -> remixer.attr("name"))
                .collect(Collectors.joining(", ")),
                Long.parseLong(remix.attr("track_length")) * 1000,
                remix.attr("file_name"),
                false,
                url,
                OCREMIX_MAIN_URL + document.selectFirst("song").attr("main_image_file")
            );

            return new OcremixAudioTrack(trackInfo, this);
        } catch (IOException e) {
            throw new FriendlyException("Failed to load info for Overcloked Remix track", SUSPICIOUS, e);
        }
    }
}
