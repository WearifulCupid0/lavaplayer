package com.sedmelluq.discord.lavaplayer.source.google.tts;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GoogleTTSAudioTrack extends DelegatedAudioTrack {
    public final GoogleTTSAudioSourceManager sourceManager;
    public final String language;
    public GoogleTTSAudioTrack(AudioTrackInfo trackInfo, String language, GoogleTTSAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
        this.language = language;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        URI uri = new URIBuilder("https://translate.google.com/translate_tts")
                .addParameter("tl", language)
                .addParameter("q", trackInfo.identifier)
                .addParameter("ie", "UTF-8")
                .addParameter("total", "1")
                .addParameter("idx", "0")
                .addParameter("textlen", Integer.toString(trackInfo.identifier.length()))
                .addParameter("client", "tw-ob")
                .build();
        try (PersistentHttpStream stream = new PersistentHttpStream(this.sourceManager.getInterface(), uri, null)) {
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
