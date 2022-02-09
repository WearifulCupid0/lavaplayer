package com.sedmelluq.discord.lavaplayer.source.ocremix;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Audio track that handles processing Overcloked Remix tracks.
 */
public class OcremixAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(OcremixAudioTrack.class);

    private static final String[] SERVERS = {
        "https://iterations.org",
        "https://ocrmirror.org",
        "https://ocr.blueblue.fr",
    };

    private final OcremixAudioSourceManager sourceManager;

    /**
     * @param trackInfo Track info
     * @param sourceManager Source manager which was used to find this track
     */
    public OcremixAudioTrack(AudioTrackInfo trackInfo, OcremixAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String url = getPlaybackUrl(httpInterface);
            log.debug("Starting Overcloked Remix track from URL: {}", url);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(url), null)) {
                processDelegate(new Mp3AudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    private String getPlaybackUrl(HttpInterface httpInterface) throws Exception {
        String url = null;

        for (final String server : SERVERS) {
            String current = server + trackInfo.identifier;
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(current))) {
                int statusCode = response.getStatusLine().getStatusCode();
                if(HttpClientTools.isSuccessWithContent(statusCode)) {
                    url = current;
                    break; //Found track url, so we don't need to keep looking into other servers.
                }
            } catch (Exception e) {
                continue; //Go to the next one.
            }
        }
        if (url == null) {
            throw new Exception("Failed to find track url");
        }

        return url;
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new OcremixAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
