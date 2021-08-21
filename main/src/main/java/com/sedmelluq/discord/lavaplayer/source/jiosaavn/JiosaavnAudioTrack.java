package com.sedmelluq.discord.lavaplayer.source.jiosaavn;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio track that handles processing Jiosaavn tracks.
 */
public class JiosaavnAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(JiosaavnAudioTrack.class);

    private final JiosaavnAudioSourceManager sourceManager;

    /**
     * @param trackInfo Track info
     * @param sourceManager Source manager which was used to find this track
     */
    public JiosaavnAudioTrack(AudioTrackInfo trackInfo, JiosaavnAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }
    
    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            log.debug("Loading JioSaavn media URL from encrypted URL: {}", trackInfo.identifier);

            String trackMediaUrl = getMediaUrl(trackInfo.identifier, httpInterface);
            log.debug("Starting JioSaavn track from URL: {}", trackMediaUrl);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackMediaUrl), null)) {
                processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    private String getMediaUrl(String encrypted_media_url, HttpInterface httpInterface) throws Exception {
        URI uri = new URIBuilder("https://www.jiosaavn.com/api.php")
        .addParameter("__call", "song.generateAuthToken")
        .addParameter("url", encrypted_media_url)
        .addParameter("_format", "json")
        .addParameter("ctx", "wap6dot0")
        .addParameter("_marker", "0")
        .build();

        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
            HttpClientTools.assertSuccessWithContent(response, "track media url");

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

            if(!json.get("auth_url").isNull()) return json.get("auth_url").text();
        }
        throw new FriendlyException("Failed to get JioSaavn media url", SUSPICIOUS, null);
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new JiosaavnAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
