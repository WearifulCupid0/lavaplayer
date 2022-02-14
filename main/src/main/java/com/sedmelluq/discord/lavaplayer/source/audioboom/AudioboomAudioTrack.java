package com.sedmelluq.discord.lavaplayer.source.audioboom;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audio track that handles processing Audioboom tracks.
 */
public class AudioboomAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(AudioboomAudioTrack.class);

    private final AudioboomAudioSourceManager sourceManager;

    /**
    * @param trackInfo Track info
    * @param sourceManager Source manager which was used to find this track
    */
    public AudioboomAudioTrack(AudioTrackInfo trackInfo, AudioboomAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String url = trackInfo.identifier + ".mp3";
            log.debug("Playing audioboom track from url: {}", url);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(url), null)) {
                processDelegate(new Mp3AudioTrack(this.trackInfo, stream), localExecutor);
                return;
            }
        }
    }


    @Override
    protected AudioTrack makeShallowClone() {
        return new AudioboomAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}