package com.sedmelluq.discord.lavaplayer.source.google.tts;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.net.URI;

public class GoogleTTSAudioTrack extends DelegatedAudioTrack {
    private static final String URL = "https://translate.google.com/translate_tts?tl=%s&q=%s&ie=UTF-8&total=1&idx=0&textlen=5&client=tw-ob";
    public final GoogleTTSAudioSourceManager sourceManager;
    public final String language;
    public GoogleTTSAudioTrack(AudioTrackInfo trackInfo, String language, GoogleTTSAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
        this.language = language;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (PersistentHttpStream stream = new PersistentHttpStream(this.sourceManager.getInterface(), URI.create(String.format(URL, this.language, trackInfo.identifier)), null)) {
            processDelegate(new Mp3AudioTrack(trackInfo, stream), executor);
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new GoogleTTSAudioTrack(trackInfo, language, sourceManager);
    }

    @Override
    public GoogleTTSAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
