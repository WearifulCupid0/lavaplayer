package com.sedmelluq.lavaplayer.source.audiomack;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class AudiomackAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(AudiomackAudioTrack.class);

    private static final int MAX_REFERENCE_REDIRECTS = 5;

    private final AudiomackAudioSourceManager sourceManager;

    public AudiomackAudioTrack(AudioTrackInfo trackInfo, AudiomackAudioSourceManager sourceManager) {
        super (trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String uri = getStreamURL(httpInterface);

            log.debug("Resolved Audiomack song {} to stream URL: {}", trackInfo.identifier, uri);

            AudioTrack delegate = loadDelegateTrack(uri);

            if (!(delegate instanceof InternalAudioTrack)) {
                throw new FriendlyException(
                        "Audiomack stream resolved to a non-playable track type.",
                        FriendlyException.Severity.SUSPICIOUS,
                        null
                );
            }

            processDelegate((InternalAudioTrack) delegate, executor);
        }
    }

    private String getStreamURL(HttpInterface httpInterface) throws Exception {
        URI requestUri = URI.create(String.format(AudiomackConstants.AUDIOMACK_SONG_STREAM, trackInfo.identifier));

        try (CloseableHttpResponse response = httpInterface.execute(new HttpPost(requestUri))) {
            HttpClientTools.assertSuccessWithContent(response, "audiomack stream url");

            String url = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8).trim();

            if (url.isBlank())
                throw new FriendlyException("Audiomack returned an empty stream URL.", FriendlyException.Severity.COMMON, null);

            return url;
        }
    }

    private AudioTrack loadDelegateTrack(String playbackUrl) throws IOException {
        AudioReference reference = new AudioReference(playbackUrl, trackInfo.title);

        for (int redirectCount = 0; redirectCount < MAX_REFERENCE_REDIRECTS; redirectCount++) {
            AudioItem item = sourceManager.loadStream(reference);

            if (item instanceof AudioTrack) {
                return (AudioTrack) item;
            }

            if (item instanceof AudioReference) {
                reference = (AudioReference) item;

                if (reference.identifier == null) {
                    throw new IOException("Audiomack stream resolved to an empty reference.");
                }

                log.debug("Following Audiomack stream reference to: {}", reference.identifier);
                continue;
            }

            if (item == null) {
                throw new IOException("Audiomack stream URL was not recognised by the HTTP source manager: " +
                        reference.identifier);
            }

            throw new IOException("Audiomack stream resolved to an unsupported audio item: " +
                    item.getClass().getName());
        }

        throw new IOException("Too many redirects while resolving Audiomack stream URL.");
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new AudiomackAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudiomackAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
