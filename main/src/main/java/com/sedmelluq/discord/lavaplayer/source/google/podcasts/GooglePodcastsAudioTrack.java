package com.sedmelluq.discord.lavaplayer.source.google.podcasts;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult.refer;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;
import static com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.getHeaderValue;

public class GooglePodcastsAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(GooglePodcastsAudioTrack.class);

    private final GooglePodcastsAudioSourceManager sourceManager;

    public GooglePodcastsAudioTrack(AudioTrackInfo trackInfo, GooglePodcastsAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getInterface()) {
            log.debug("Starting Google podcast from URL: {}", trackInfo.identifier);

            try (PersistentHttpStream inputStream = new PersistentHttpStream(httpInterface, new URI(trackInfo.identifier), null)) {
                int statusCode = inputStream.checkStatusCode();
                String redirectUrl = HttpClientTools.getRedirectLocation(trackInfo.identifier, inputStream.getCurrentResponse());

                MediaContainerDetectionResult result = null;
                if (redirectUrl != null) {
                    result = refer(null, new AudioReference(redirectUrl, null));
                } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                    throw new FriendlyException("Invalid URL.", SUSPICIOUS, new IllegalStateException("Status code " + statusCode));
                } else if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                    throw new FriendlyException("URL not playable.", SUSPICIOUS, new IllegalStateException("Status code " + statusCode));
                } else {
                    MediaContainerHints hints = MediaContainerHints.from(getHeaderValue(inputStream.getCurrentResponse(), "Content-Type"), null);
                    result = new MediaContainerDetection(sourceManager.getMediaContainerRegistry(), new AudioReference(trackInfo.identifier, null), inputStream, hints).detectContainer();
                }
                processDelegate((InternalAudioTrack) result.getContainerDescriptor().createTrack(trackInfo, inputStream), localExecutor);
            }
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new GooglePodcastsAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public GooglePodcastsAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}