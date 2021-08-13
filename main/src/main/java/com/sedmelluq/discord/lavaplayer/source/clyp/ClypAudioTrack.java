package com.sedmelluq.discord.lavaplayer.source.clyp;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import java.io.IOException;
import java.net.URI;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audio track that handles processing Clyp tracks.
 */
public class ClypAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(ClypAudioTrack.class);

    private final ClypAudioSourceManager sourceManager;

    /**
    * @param trackInfo Track info
    * @param sourceManager Source manager which was used to find this track
    */
    public ClypAudioTrack(AudioTrackInfo trackInfo, ClypAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            log.debug("Loading clyp audio from identifier: {}", trackInfo.identifier);

            String trackMediaUrl = getTrackMediaUrl(httpInterface);
            log.debug("Starting clyp audio from URL: {}", trackMediaUrl);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackMediaUrl), null)) {
                processDelegate(new Mp3AudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    private String getTrackMediaUrl(HttpInterface httpInterface) throws IOException {
        URI url = URI.create("https://api.clyp.it/" + sourceManager.getIdentifier(trackInfo.identifier));
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(url))) {
            HttpClientTools.assertSuccessWithContent(response, "audio page");

            JsonBrowser trackInfo = JsonBrowser.parse(response.getEntity().getContent());

            return trackInfo.get("Mp3Url").isNull() ? trackInfo.get("SecureMp3Url").text() : trackInfo.get("Mp3Url").text();
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new ClypAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
