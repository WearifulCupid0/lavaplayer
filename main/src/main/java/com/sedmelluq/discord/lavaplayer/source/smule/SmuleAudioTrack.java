package com.sedmelluq.discord.lavaplayer.source.smule;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.jsoup.nodes.Document;
import org.jsoup.Jsoup;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Audio track that handles processing Smule tracks.
 */
public class SmuleAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(SmuleAudioTrack.class);

    private final SmuleAudioSourceManager sourceManager;

    /**
     * @param trackInfo Track info
     * @param sourceManager Source manager which was used to find this track
     */
    public SmuleAudioTrack(AudioTrackInfo trackInfo, SmuleAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String playbackUrl = loadPlaybackUrl(httpInterface);

            log.debug("Starting Smule track from URL: {}", playbackUrl);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), null)) {
                processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    private String loadPlaybackUrl(HttpInterface httpInterface) throws IOException {
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(trackInfo.identifier))) {
            HttpClientTools.assertSuccessWithContent(response, "track page");

            String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            Document document = Jsoup.parse(html);
            
            String redirString = document.selectFirst("meta[name=twitter:player:stream]").attr("content");
            if (redirString == null || redirString.isEmpty()) {
                throw new IOException("Redirect url not found on track page.");
            }

            URI redirUrl =  URI.create(redirString.replaceAll("amp;", ""));
            try (CloseableHttpResponse res = httpInterface.execute(new HttpGet(redirUrl))) {
                HttpClientContext context = httpInterface.getContext();
                List<URI> redirects = context.getRedirectLocations();
                if (redirects != null && !redirects.isEmpty()) {
                    return redirects.get(0).toString();
                } else {
                    throw new IOException("Failed to follow playback redirect location.");
                }
            }
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new SmuleAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
