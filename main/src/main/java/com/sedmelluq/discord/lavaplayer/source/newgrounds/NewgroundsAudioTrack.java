package com.sedmelluq.discord.lavaplayer.source.newgrounds;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
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
 * Audio track that handles processing NewGrounds tracks.
 */
public class NewgroundsAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(NewgroundsAudioTrack.class);

    private final NewgroundsAudioSourceManager sourceManager;

    /**
     * @param trackInfo     Track info
     * @param sourceManager Source manager which was used to find this track
     */
    public NewgroundsAudioTrack(AudioTrackInfo trackInfo, NewgroundsAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            log.debug("Starting NewGrounds track from URL: {}", this.trackInfo.identifier);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(this.trackInfo.identifier), null)) {
                processDelegate(this.trackInfo.identifier.contains(".mp3")
                ? new Mp3AudioTrack(trackInfo, stream)
                : new MpegAudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new NewgroundsAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public NewgroundsAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}