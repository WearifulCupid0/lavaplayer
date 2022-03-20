package com.sedmelluq.discord.lavaplayer.source.soundgasm;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
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
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio source manager which detects soundgasm tracks by URL.
 */
public class SoundgasmAudioSourceManager implements HttpConfigurable, AudioSourceManager {
    private static final String SOUNDGASM_CLIP_URL = "https://soundgasm.net/u/%s/%s";
    private static final String SOUNDGASM_URL_REGEX = "^(?:http://|https://|)(?:www\\.|)soundgasm\\.net/u/([a-zA-Z0-9-_]+)/([a-zA-Z0-9-_]+)";
    private static final String SOUNDGASM_TITLE_REGEX = "<div class=\"jp-title\" aria-label=\"title\">(.*)<\\/div>";
    private static final String SOUNDGASM_IDENTIFIER_REGEX = "^(?:http://|https://|)(?:www\\.|)soundgasm\\.net/sounds/([a-zA-Z0-9-_]+)\\.m4a";

    private static final Pattern soundgasmUrlPattern = Pattern.compile(SOUNDGASM_URL_REGEX);
    private static final Pattern soundgasmTitlePattern = Pattern.compile(SOUNDGASM_TITLE_REGEX);
    private static final Pattern soundgasmIdentifierPattern = Pattern.compile(SOUNDGASM_IDENTIFIER_REGEX);

    private final HttpInterfaceManager httpInterfaceManager;

    public SoundgasmAudioSourceManager() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "soundgasm";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        Matcher m = soundgasmUrlPattern.matcher(reference.identifier);

        if (m.find()) {
            return extractTrackFromPage(m.group(1), m.group(2));
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
        return new SoundgasmAudioTrack(trackInfo, this);
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

    private AudioTrack extractTrackFromPage(String author, String path) {
        String url = String.format(SOUNDGASM_CLIP_URL, author, path);
        try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(url))) {
            HttpClientTools.assertSuccessWithContent(response, "audio page");

            String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);

            String title = "Unknown title";
            Matcher titleMatcher = soundgasmTitlePattern.matcher(html);
            if (titleMatcher.find()) {
                title = titleMatcher.group(1);
            }

            String identifier = null;
            Matcher identifierMatcher = soundgasmIdentifierPattern.matcher(html);
            if (identifierMatcher.find()) {
                identifier = identifierMatcher.group(1);
            }

            if (identifier == null || identifier.isEmpty()) {
                throw new IOException("Audio id not found on soundgasm page.");
            }

            AudioTrackInfo trackInfo = new AudioTrackInfo(
                title,
                author,
                Units.DURATION_MS_UNKNOWN,
                identifier,
                false,
                url
            );

            return new SoundgasmAudioTrack(trackInfo, this);
        } catch (IOException e) {
            throw new FriendlyException("Failed to load info for soundgasm audio", SUSPICIOUS, e);
        }
    }
}
