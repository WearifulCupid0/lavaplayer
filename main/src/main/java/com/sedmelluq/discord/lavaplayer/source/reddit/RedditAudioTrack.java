package com.sedmelluq.discord.lavaplayer.source.reddit;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class RedditAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(RedditAudioTrack.class);

    private final RedditAudioSourceManager sourceManager;

    /**
     * @param trackInfo Track info
     * @param sourceManager Source manager which was used to find this track
     */
    public RedditAudioTrack(AudioTrackInfo trackInfo, RedditAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String playbackUrl = "https://v.redd.it/" + trackInfo.identifier + "/DASH_audio.mp4";

            log.debug("Starting Reddit track from URL: {}", playbackUrl);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), null)) {
                processDelegate(new MpegAudioTrack(trackInfo, stream), executor);
            }
        } catch (IOException e) {
            throw new FriendlyException("Loading track from Reddit failed.", SUSPICIOUS, e);
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new RedditAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public RedditAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}