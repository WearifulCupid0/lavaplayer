package com.sedmelluq.discord.lavaplayer.source.saavn;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

/**
 * Audio track that handles processing JioSaavn tracks.
 */
public class SaavnAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(SaavnAudioTrack.class);

    private final SaavnAudioSourceManager sourceManager;

    /**
    * @param trackInfo Track info
    * @param sourceManager Source manager which was used to find this track
    */
    public SaavnAudioTrack(AudioTrackInfo trackInfo, SaavnAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            JsonBrowser songInfo = sourceManager.apiRequester.track(trackInfo.identifier);
            songInfo = songInfo.get("more_info").isNull() ? songInfo : songInfo.get("more_info");
            if(songInfo.get("encrypted_media_url").isNull()) throw new FriendlyException("Encrypted media url not found.", COMMON, null);
            JsonBrowser mediaResponse = sourceManager.apiRequester.decodeTrack(songInfo.get("encrypted_media_url").text());
            if(mediaResponse.get("status").text() != "success" || mediaResponse.get("auth_url").isNull()) throw new FriendlyException("Failed to get media url.", COMMON, null);
            String mediaURL = mediaResponse.get("auth_url").text();
            log.debug("Starting saavn track from URL: {}", mediaURL);
            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(mediaURL), null)) {
                processDelegate(new Mp3AudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    @Override
    public AudioTrack makeClone() {
        return new SaavnAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}

