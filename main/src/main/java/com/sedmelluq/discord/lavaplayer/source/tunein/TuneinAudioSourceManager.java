package com.sedmelluq.discord.lavaplayer.source.tunein;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
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
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;
import static com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN;

public class TuneinAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String TUNEIN_URL = "https://tunein.com/radio/%s/";
    private static final String TUNEIN_REGEX = "^(?:http://|https://|)(?:www\\.|)tunein\\.com/radio/([a-zA-Z0-9-_]+)";
    private static final String RADIO_DATA_REGEX = "window\\.INITIAL_STATE=(.*);";

    private static final Pattern tuneinPattern = Pattern.compile(TUNEIN_REGEX);
    private static final Pattern radioDataPattern = Pattern.compile(RADIO_DATA_REGEX);

    private final HttpInterfaceManager httpInterfaceManager;

    public TuneinAudioSourceManager() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "tunein";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference) {
        Matcher m = tuneinPattern.matcher(reference.identifier);

        if (m.find()) {
            return extractVideoUrlFromPage(String.format(TUNEIN_URL, m.group(1)));
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
        return new TuneinAudioTrack(trackInfo, this);
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

    private AudioTrack extractVideoUrlFromPage(String url) {
        try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(url))) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("Unexpected status code from TuneIn radio page: " + statusCode);
            }

            String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            Matcher m = radioDataPattern.matcher(html);
            if (!m.find()) {
                throw new IOException("Radio data not present on radio page");
            }
            JsonBrowser json = JsonBrowser.parse(m.group(1));
            JsonBrowser radio = json.get("profiles").values().get(0);

            if (radio.isNull()) {
                throw new IOException("Radio data info is empty");
            }

            AudioTrackInfo trackInfo = new AudioTrackInfo(
                radio.get("title").safeText(),
                "Unknown author",
                DURATION_MS_UNKNOWN,
                radio.get("guideId").text(),
                true,
                url,
                radio.get("image").text()
            );

            return new TuneinAudioTrack(trackInfo, this);
        } catch (IOException e) {
            throw new FriendlyException("Failed to load info for Tunein radio", SUSPICIOUS, e);
        }
    }
}
