package com.sedmelluq.discord.lavaplayer.source.bilibili;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.Units;
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

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sedmelluq.discord.lavaplayer.source.bilibili.BilibiliConstants.VIEW_API;
import static com.sedmelluq.discord.lavaplayer.source.bilibili.BilibiliConstants.PLAYER_API;

/**
 * Audio track that handles processing BiliBili tracks.
 */
public class BilibiliAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(BilibiliAudioTrack.class);

    private final BilibiliAudioSourceManager sourceManager;
    /**
      * @param trackInfo Track info
      * @param sourceManager Source manager which was used to find this track
      */
    public BilibiliAudioTrack(AudioTrackInfo trackInfo, BilibiliAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String playbackUrl = loadPlaybackUrl(httpInterface);
            log.debug("Starting BiliBili track from URL: {}", playbackUrl);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), Units.CONTENT_LENGTH_UNKNOWN)) {
                processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    private String loadPlaybackUrl(HttpInterface httpInterface) throws Exception {
        try (CloseableHttpResponse trackMetaResponse = httpInterface.execute(new HttpGet(VIEW_API + "?bvid=" + trackInfo.identifier))) {
            HttpClientTools.assertSuccessWithContent(trackMetaResponse, "video api response");

            JsonBrowser trackMeta = JsonBrowser.parse(trackMetaResponse.getEntity().getContent());

            URI uri = new URIBuilder(PLAYER_API)
            .addParameter("bvid", trackInfo.identifier)
            .addParameter("cid", trackMeta.get("data").get("cid").text())
            .addParameter("qn", "0").addParameter("fnval", "80").addParameter("fourk", "1")
            .build();

            HttpGet request = new HttpGet(uri);

            try (CloseableHttpResponse response = httpInterface.execute(request)) {
                HttpClientTools.assertSuccessWithContent(response, "player api");

                JsonBrowser trackMetaData = JsonBrowser.parse(response.getEntity().getContent());
      
                return trackMetaData.get("data").get("dash").get("audio").values().get(0).get("base_url").text();
            }
        }
    }


    @Override
    protected AudioTrack makeShallowClone() {
      return new BilibiliAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
      return sourceManager;
    }
}