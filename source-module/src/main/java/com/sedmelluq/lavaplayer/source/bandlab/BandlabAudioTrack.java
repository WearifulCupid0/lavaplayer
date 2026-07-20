package com.sedmelluq.lavaplayer.source.bandlab;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.net.URI;

public class BandlabAudioTrack extends DelegatedAudioTrack {
    private final BandlabAudioSourceManager sourceManager;

    public BandlabAudioTrack(AudioTrackInfo trackInfo, BandlabAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String fileUrl = trackInfo.identifier;

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(fileUrl), Units.CONTENT_LENGTH_UNKNOWN)) {
                processDelegate(new MpegAudioTrack(trackInfo, stream), executor);
            }
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new BandlabAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public BandlabAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
