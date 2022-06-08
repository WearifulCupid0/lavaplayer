package com.sedmelluq.discord.lavaplayer.source.rumble;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class RumbleAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(RumbleAudioTrack.class);
    private final RumbleAudioSourceManager sourceManager;

    private static final String VIDEO_URL = "https://rumble.com/embedJS/u3?request=video&ver=2&v=%s";

    /**
     * @param trackInfo Track info
     */
    public RumbleAudioTrack(AudioTrackInfo trackInfo, RumbleAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String playbackUrl = loadPlaybackUrl(httpInterface);

            log.debug("Starting Rumble track from URL: {}", playbackUrl);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), null)) {
                processDelegate(new MpegAudioTrack(trackInfo, stream), executor);
            }
        } catch (IOException e) {
            throw new FriendlyException("Loading track from Rumble failed.", SUSPICIOUS, e);
        }
    }

    private String loadPlaybackUrl(HttpInterface httpInterface) throws IOException {
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(String.format(VIDEO_URL, trackInfo.identifier)))) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("Unexpected status code from playback data page: " + statusCode);
            }

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

            if (json.get("ua").get("mp4").isNull()) throw new IOException("Could not find a playable mp4 url");

            return json.get("ua").get("mp4").get("360").get("url").text();
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new RumbleAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public RumbleAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}