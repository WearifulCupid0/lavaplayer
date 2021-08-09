package com.sedmelluq.discord.lavaplayer.source.saavn;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;

import org.apache.commons.io.IOUtils;
import java.nio.charset.StandardCharsets;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

/**
 * Audio track that handles processing JioSaavn tracks.
 */
public class SaavnAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(SaavnAudioTrack.class);

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
            String mediaURL = getMediaUrl(httpInterface);
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

    private String getEncodedURL(HttpInterface httpInterface) throws IOException {
        URI uri = URI.create("https://www.jiosaavn.com/api.php?__call=webapi.get&type=song&ctx=web6dot0&_format=json&_marker=0&token=" + trackInfo.identifier);
        HttpGet get = new HttpGet(uri);
        RequestConfig config = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
        get.setHeader("Accept", "application/json");
        get.setConfig(config);
        try (CloseableHttpResponse response = httpInterface.execute(get)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new IOException("Invalid status code: " + statusCode);
            }
            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            return json.get("songs").index(0).get("encrypted_media_url").text();
        }
    }

    private String getMediaUrl(HttpInterface httpInterface) throws IOException {
        String encoded = getEncodedURL(httpInterface);
        URI uri = URI.create("https://www.jiosaavn.com/api.php?__call=song.generateAuthToken&_format=json&_marker=0url=" + encoded);
        RequestConfig config = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
        HttpGet get = new HttpGet(uri);
        get.setConfig(config);
        get.setHeader("Accept", "application/json");
        try (CloseableHttpResponse response = httpInterface.execute(get)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new IOException("Invalid status code: " + statusCode);
            }
            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            return json.get("auth_url").safeText();
        }
    }
}

