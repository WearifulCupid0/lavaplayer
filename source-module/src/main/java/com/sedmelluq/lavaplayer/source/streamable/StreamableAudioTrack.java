package com.sedmelluq.lavaplayer.source.streamable;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;

/**
 * Audio track that handles processing Streamable tracks.
 */
public class StreamableAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(StreamableAudioTrack.class);

    private final StreamableAudioSourceManager sourceManager;

    /**
     * @param trackInfo Track info
     * @param sourceManager Source manager which was used to find this track
     */
    public StreamableAudioTrack(AudioTrackInfo trackInfo, StreamableAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String playbackUrl = loadPlaybackUrl(httpInterface);

            log.debug("Starting Streamable track from URL: {}", playbackUrl);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), null)) {
                processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    private String loadPlaybackUrl(HttpInterface httpInterface) throws IOException {
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(StreamableAudioSourceManager.API_URL + trackInfo.identifier))) {
            HttpClientTools.assertSuccessWithContent(response, "video page");

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            
            return json.get("files").get("mp4").get("url").text();
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new StreamableAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public StreamableAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
