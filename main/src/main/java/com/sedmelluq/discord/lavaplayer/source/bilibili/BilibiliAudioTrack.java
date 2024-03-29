package com.sedmelluq.discord.lavaplayer.source.bilibili;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
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

import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sedmelluq.discord.lavaplayer.source.bilibili.BilibiliConstants.PLAYER_API;
import static com.sedmelluq.discord.lavaplayer.source.bilibili.BilibiliConstants.VIEW_API;

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
        final String[] ids = trackInfo.identifier.split("/");
        URI uri = new URIBuilder(PLAYER_API)
        .addParameter("bvid", ids[0])
        .addParameter("cid", ids[1] == null ? getVideoCid(httpInterface) : ids[1])
        .addParameter("qn", "0")
        .addParameter("fnval", "80")
        .addParameter("fourk", "1").build();

        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
            HttpClientTools.assertSuccessWithContent(response, "player api");

            JsonBrowser trackMetaData = JsonBrowser.parse(response.getEntity().getContent());
            
            return trackMetaData.get("data").get("dash").get("audio").index(0).get("base_url").text();
        }
    }

    private String getVideoCid(HttpInterface httpInterface) throws IOException {
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(VIEW_API + "?bvid=" + trackInfo.identifier))) {
            HttpClientTools.assertSuccessWithContent(response, "video api response");

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            if (json.get("code").as(Integer.class) != 0) {
                throw new IOException("Video api didn't respond.");
            }
            return json.get("data").get("cid").text();
        }
    }


    @Override
    protected AudioTrack makeShallowClone() {
      return new BilibiliAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public BilibiliAudioSourceManager getSourceManager() {
      return sourceManager;
    }
}