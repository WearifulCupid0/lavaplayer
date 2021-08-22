package com.sedmelluq.discord.lavaplayer.source.dailymotion;

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

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Audio track that handles processing DailyMotion videos.
 */
public class DailyMotionAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(DailyMotionAudioTrack.class);
    private static final Pattern progressivePattern = Pattern.compile(",PROGRESSIVE-URI=\"(.*)\"");

    private final DailyMotionAudioSourceManager sourceManager;

    /**
    * @param trackInfo Track info
    * @param sourceManager Source manager which was used to find this track
    */
    public DailyMotionAudioTrack(AudioTrackInfo trackInfo, DailyMotionAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            log.debug("Starting loading DailyMotion video from identifier: {}", trackInfo.identifier);
            String playerUrl = getUri(httpInterface);
            String trackMediaUrl = getProgressiveUrl(playerUrl, httpInterface);

            log.debug("Starting DailyMotion video from URL: {}", trackMediaUrl);
            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackMediaUrl), null)) {
                processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    private String getUri(HttpInterface httpInterface) throws Exception {
        String uri = "https://www.dailymotion.com/player/metadata/video/" + trackInfo.identifier;
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(new URI(uri)))) {
            HttpClientTools.assertSuccessWithContent(response, "player response");
            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            String url = json.get("qualities").get("auto").index(0).get("url").text();

            if (url == null) {
                throw new FriendlyException("This video is not playable", FriendlyException.Severity.COMMON, null);
            }
            return url;
        }
    }

    private String getProgressiveUrl(String playerUrl, HttpInterface httpInterface) throws Exception {
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(playerUrl))) {
            HttpClientTools.assertSuccessWithContent(response, "player response");
            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            log.info(responseText);
            Matcher matcher = progressivePattern.matcher(responseText);
            if (!matcher.find()) {
                throw new FriendlyException("Failed to get progressive URL", FriendlyException.Severity.SUSPICIOUS, null);
            }

            return matcher.group(1);
        }
    }

    @Override
    public AudioTrack makeClone() {
        return new DailyMotionAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
