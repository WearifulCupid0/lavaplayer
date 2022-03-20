package com.sedmelluq.discord.lavaplayer.source.soundgasm;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
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
 * Audio track that handles processing Soundgasm tracks.
 */
public class SoundgasmAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(SoundgasmAudioTrack.class);

    private final SoundgasmAudioSourceManager sourceManager;

    /**
    * @param trackInfo Track info
    * @param sourceManager Source manager which was used to find this track
    */
    public SoundgasmAudioTrack(AudioTrackInfo trackInfo, SoundgasmAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String trackMediaUrl = String.format("https://media.soundgasm.net/sounds/%s.m4a", trackInfo.identifier);
            log.debug("Starting Soundgasm track from URL: {}", trackMediaUrl);
            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackMediaUrl), null)) {
                processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    @Override
    public AudioTrack makeClone() {
        return new SoundgasmAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public SoundgasmAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}