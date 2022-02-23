package com.sedmelluq.discord.lavaplayer.source.audioboom;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
            String url = loadPlaybackUrl(httpInterface);
            log.debug("Playing audioboom track from url: {}", url);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(url), null)) {
                processDelegate(new Mp3AudioTrack(this.trackInfo, stream), localExecutor);
                return;
            }
        }
    }

    private String loadPlaybackUrl(HttpInterface httpInterface) throws IOException {
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(trackInfo.identifier))) {
            HttpClientTools.assertSuccessWithContent(response, "audio page");

            String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            Document document = Jsoup.parse(html);
            
            String redirString = document.selectFirst("meta[property=og:audio]").attr("content");
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
        return new AudioboomAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}