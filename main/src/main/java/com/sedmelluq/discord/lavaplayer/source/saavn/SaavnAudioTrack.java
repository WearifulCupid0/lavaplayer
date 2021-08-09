package com.sedmelluq.discord.lavaplayer.source.saavn;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;

import org.apache.commons.io.IOUtils;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

/**
 * Audio track that handles processing JioSaavn tracks.
 */
public class SaavnAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(SaavnAudioTrack.class);
    private static final String AUTHTOKEN_URL = "https://www.jiosaavn.com/api.php?__call=song.generateAuthToken&url=%s&_format=json&_marker=0";

    private final SaavnAudioSourceManager sourceManager;

    /**
    * @param trackInfo Track info
    * @param sourceManager Source manager which was used to find this track
    */
    public SaavnAudioTrack(AudioTrackInfo trackInfo, SaavnAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String rawURL = this.getURL(httpInterface);
            String mediaURL = this.getRedirectURL(rawURL, httpInterface);
            log.debug("Starting saavn track from URL: {}", mediaURL);
            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(mediaURL), null)) {
                processDelegate(new Mp3AudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    @Override
    public AudioTrack makeClone() {
        return new SaavnAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }

    private String getURL(HttpInterface httpInterface) throws IOException {
        URI uri = URI.create(String.format(AUTHTOKEN_URL, trackInfo.identifier));
        HttpGet get = new HttpGet(uri);
        RequestConfig config = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
        get.setConfig(config);
        get.setHeader("Accept", "application/json");
        try (CloseableHttpResponse response = httpInterface.execute(get)) {
            HttpClientTools.assertSuccessWithContent(response, "auth response");

            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            String mediaURL = json.get("auth_url").safeText();
            return mediaURL;
        }
    }

    private String getRedirectURL(String url, HttpInterface httpInterface) throws IOException {
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(url))) {
            HttpClientTools.assertSuccessWithContent(response, "redirect response");
            HttpClientContext context = httpInterface.getContext();
            List<URI> redirects = context.getRedirectLocations();
            if (redirects != null && !redirects.isEmpty()) {
                return redirects.get(0).toString();
            }
            return null;
        }
    }
}

