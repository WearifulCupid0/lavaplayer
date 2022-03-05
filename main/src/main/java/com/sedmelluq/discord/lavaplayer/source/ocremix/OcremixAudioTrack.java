package com.sedmelluq.discord.lavaplayer.source.ocremix;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Audio track that handles processing Overcloked Remix tracks.
 */
public class OcremixAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(OcremixAudioTrack.class);

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
            log.debug("Starting Overcloked Remix track from URL: {}", trackInfo.identifier);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackInfo.identifier), null)) {
                processDelegate(new Mp3AudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new OcremixAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public OcremixAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
