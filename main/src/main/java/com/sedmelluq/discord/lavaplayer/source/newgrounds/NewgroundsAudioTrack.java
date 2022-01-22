package com.sedmelluq.discord.lavaplayer.source.newgrounds;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio track that handles processing NewGrounds tracks.
 */
public class NewgroundsAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(NewgroundsAudioTrack.class);

    private static final String[] SOURCE_RESOLUTIONS = new String[]{"1080p", "720p", "360p"};

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
            String playbackUrl = loadPlaybackUrl(httpInterface);

            log.debug("Starting NewGrounds track from URL: {}", playbackUrl);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), null)) {
                processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    private String loadPlaybackUrl(HttpInterface httpInterface) throws IOException {
        Matcher matcher = NewgroundsAudioSourceManager.URL_PATTERN.matcher(trackInfo.uri);
        if(!matcher.matches()) {
            throw new FriendlyException("unknown uri format", SUSPICIOUS, null);
        }
        switch (matcher.group(1)) {
            case "portal/view":
                return loadVideoPlaybackUrl(httpInterface);
            case "audio/listen":
                return loadAudioPlaybackUrl(httpInterface);
        }
        throw new FriendlyException("unknown uri format", SUSPICIOUS, null);
    }

    private String loadVideoPlaybackUrl(HttpInterface httpInterface) throws IOException, FriendlyException {
        JsonBrowser json = sourceManager.loadJsonFromUrl(httpInterface, "https://www.newgrounds.com/portal/video/" + trackInfo.identifier);
        if (json == null || !json.get("error").isNull()) {
            throw new FriendlyException("Track information not present on the page.", SUSPICIOUS, null);
        }

        JsonBrowser allSources = json.get("sources");
        for (String resolution : SOURCE_RESOLUTIONS) {
            JsonBrowser sources = allSources.get(resolution);
            if (!sources.isNull()) {
                return sources.index(0).get("src").text();
            }
        }

        throw new FriendlyException("No matching resolution found.", SUSPICIOUS, null);
    }

    private String loadAudioPlaybackUrl(HttpInterface httpInterface) throws IOException, FriendlyException {
        JsonBrowser json = sourceManager.loadJsonFromUrl(httpInterface, "https://www.newgrounds.com/audio/load/" + trackInfo.identifier);
        if (json == null) {
            throw new FriendlyException("Track information not present on the page.", SUSPICIOUS, null);
        }

        return json.get("sources").index(0).get("src").text();
    }


    @Override
    protected AudioTrack makeShallowClone() {
        return new NewgroundsAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}