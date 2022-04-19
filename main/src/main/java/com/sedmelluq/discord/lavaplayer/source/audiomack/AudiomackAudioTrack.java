package com.sedmelluq.discord.lavaplayer.source.audiomack;

import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

public class AudiomackAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(AudiomackAudioTrack.class);
    private static final String API_URL = "https://www.audiomack.com/api/music/url/song/%s/%s";

    private final AudiomackAudioSourceManager sourceManager;

    public AudiomackAudioTrack(AudioTrackInfo trackInfo, AudiomackAudioSourceManager sourceManager) {
        super (trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String url = getPlaybackURL(httpInterface);
            if (url == null) throw new IOException("Audiomack playback url not found.");

            log.debug("Starting to play Audiomack track from url {}", url);
            
            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(url), null)) {
                processDelegate(new Mp3AudioTrack(trackInfo, stream), executor);
            }
        }
    }

    private String getPlaybackURL(HttpInterface httpInterface) {
        String[] parameters = this.sourceManager.parseURL(this.trackInfo.identifier);
        if (parameters == null) return null;
        String apiUrl = String.format(API_URL, parameters[0], parameters[1]);
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(URI.create(apiUrl)))) {
            HttpClientTools.assertSuccessWithContent(response, "api response");

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            if (json.isNull() || json.get("url").isNull() || json.get("url").safeText().isEmpty()) {
                throw new IOException("Failed to fetch playback url.");
            }

            return json.get("url").safeText();
        } catch (IOException e) {
            throw new FriendlyException("Failed to fetch audiomack playback url", COMMON, e);
        }
    }

    @Override
    protected AudiomackAudioTrack makeShallowClone() {
        return new AudiomackAudioTrack(this.trackInfo, this.sourceManager);
    }

    @Override
    public AudiomackAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
