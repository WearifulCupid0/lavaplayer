package com.sedmelluq.discord.lavaplayer.source.tiktok;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Audio track that handles processing TikTok videos.
 */
public class TikTokAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(TikTokAudioTrack.class);

    private final TikTokAudioSourceManager sourceManager;

    /**
    * @param trackInfo Track info
    * @param sourceManager Source manager which was used to find this track
    */
    public TikTokAudioTrack(AudioTrackInfo trackInfo, TikTokAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String[] splitted = trackInfo.identifier.split("/");
            String user = splitted[0];
            String id = splitted[1];
            JsonBrowser item = sourceManager.getMediaExtractor().fetchFromPage(user, id, httpInterface);
            String trackMediaUrl = getTrackMediaUrl(item.get("video"));
            log.debug("Starting tiktok track from URL: {}", trackMediaUrl);
            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackMediaUrl), null)) {
                processDelegate(new Mp3AudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    @Override
    public AudioTrack makeClone() {
        return new TikTokAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }

    private String getTrackMediaUrl(JsonBrowser video) {
        if(!video.get("downloadAddr").isNull()) {
            return video.get("downloadAddr").text();
        }
        if(!video.get("playAddr").isNull()) {
            return video.get("playAddr").text();
        }
        return null;
    }
}

