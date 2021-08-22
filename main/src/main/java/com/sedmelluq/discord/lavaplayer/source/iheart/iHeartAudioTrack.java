package com.sedmelluq.discord.lavaplayer.source.iheart;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
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
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult.refer;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;
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
            log.info(mediaUrl);

            try (PersistentHttpStream inputStream = new PersistentHttpStream(httpInterface, new URI(mediaUrl), null)) {
                int statusCode = inputStream.checkStatusCode();
                String redirectUrl = HttpClientTools.getRedirectLocation(mediaUrl, inputStream.getCurrentResponse());

                MediaContainerDetectionResult result = null;
                if (redirectUrl != null) {
                    result = refer(null, new AudioReference(redirectUrl, null));
                } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                    throw new FriendlyException("Invalid URL.", SUSPICIOUS, new IllegalStateException("Status code " + statusCode));
                } else if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                    throw new FriendlyException("URL not playable.", SUSPICIOUS, new IllegalStateException("Status code " + statusCode));
                } else {
                    MediaContainerHints hints = MediaContainerHints.from(getHeaderValue(inputStream.getCurrentResponse(), "Content-Type"), null);
                    result = new MediaContainerDetection(sourceManager.getMediaContainerRegistry(), new AudioReference(mediaUrl, null), inputStream, hints).detectContainer();
                }
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
        if (!result.get("hits").isNull()) {
            JsonBrowser hit = result.get("hits").index(0);
            if (!hit.isNull()) {
                JsonBrowser streams = hit.get("streams");
                if(!streams.isNull()) {
                    return !streams.get("shoutcast_stream").isNull()
                    ? streams.get("shoutcast_stream").text()
                    : !streams.get("secure_shoutcast_stream").isNull()
                    ? streams.get("secure_shoutcast_stream").text()
                    : !streams.get("pls_stream").isNull()
                    ? streams.get("pls_stream").text()
                    : streams.get("secure_pls_stream").text();
                }
            }
        }
        if (!result.get("episode").isNull()) {
            JsonBrowser mediaUrl = result.get("episode").get("mediaUrl");
            if (!mediaUrl.isNull()) {
                String url = mediaUrl.safeText();
                if (url.length() > 0) return url;
            }
        }
        return null;
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
