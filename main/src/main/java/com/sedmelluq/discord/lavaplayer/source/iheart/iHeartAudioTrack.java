package com.sedmelluq.discord.lavaplayer.source.iheart;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.getHeaderValue;

/**
 * Audio track that handles processing iHeart tracks.
 */
public class iHeartAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(iHeartAudioTrack.class);

    private final iHeartAudioSourceManager sourceManager;

    /**
     * @param trackInfo Track info
     * @param containerTrackFactory Container track factory - contains the probe with its parameters.
     * @param sourceManager Source manager used to load this track
     */
    public iHeartAudioTrack(AudioTrackInfo trackInfo, iHeartAudioSourceManager sourceManager) {

    super(trackInfo);

    this.sourceManager = sourceManager;
  }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            log.debug("Loading iHeart media URL from identifier: {}", trackInfo.identifier);

            String mediaUrl = getMediaUrl(trackInfo.identifier, httpInterface);
            log.debug("Starting iHeart track from URL: {}", mediaUrl);

            try (PersistentHttpStream inputStream = new PersistentHttpStream(httpInterface, new URI(mediaUrl), Units.CONTENT_LENGTH_UNKNOWN)) {
                MediaContainerHints hints = MediaContainerHints.from(getHeaderValue(inputStream.getCurrentResponse(), "Content-Type"), null);
                MediaContainerDetectionResult result = new MediaContainerDetection(sourceManager.getMediaContainerRegistry(), new AudioReference(mediaUrl, null), inputStream, hints).detectContainer();
                processDelegate((InternalAudioTrack) result.getContainerDescriptor().createTrack(trackInfo, inputStream), localExecutor);
            }
        }
    }

    private String getMediaUrl(String identifier, HttpInterface httpInterface) throws Exception {
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(URI.create(getRequestUrl())))) {
            HttpClientTools.assertSuccessWithContent(response, "media response");
            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            return getMediaUrl(json);
        }
    }

    private String getRequestUrl() {
        if (trackInfo.isStream) return "https://api.iheart.com/api/v2/content/liveStations?id=" + trackInfo.identifier;
        else return "https://api.iheart.com/api/v3/podcast/episodes/" + trackInfo.identifier;
    }

    private String getMediaUrl(JsonBrowser result) {
        if (trackInfo.isStream) {
            return result.get("hits").index(0).get("streams").get("shoutcast_stream").text();
        } else {
            return result.get("episode").get("mediaUrl").text();
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new iHeartAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
