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
            String encoded = getEncodedURL(httpInterface);
            String mediaURL = getUrlWithEncoded(encoded, httpInterface);
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
        get.setHeader("Accept", "application/json");
        try (CloseableHttpResponse response = httpInterface.execute(get)) {
            HttpClientTools.assertSuccessWithContent(response, "encoded url");
            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            return json.get("songs").index(0).get("encrypted_media_url").text();
        }
    }

    private String getUrlWithEncoded(String encoded, HttpInterface httpInterface) throws IOException {
        URI uri = URI.create("https://www.jiosaavn.com/api.php?__call=song.generateAuthToken&_format=json&_marker=0url=" + encoded);
        HttpGet get = new HttpGet(uri);
        get.setHeader("Accept", "application/json");
        try (CloseableHttpResponse response = httpInterface.execute(get)) {
            HttpClientTools.assertSuccessWithContent(response, "media url");
            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            if(json.get("status").text() != "success" || json.get("auth_url").isNull()) return null;
            return json.get("auth_url").text();
        }
    }
}

