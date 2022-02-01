package com.sedmelluq.discord.lavaplayer.source.bilibili;

import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.convertToMapLayout;

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

  private String loadPlaybackUrl(HttpInterface httpInterface) throws IOException {
      try (CloseableHttpResponse trackMetaResponse = httpInterface.execute(new HttpGet("https://api.bilibili.com/x/web-interface/view?bvid=" + trackInfo.identifier))) {
                int trackMetastatusCode = trackMetaResponse.getStatusLine().getStatusCode();
                if (!HttpClientTools.isSuccessWithContent(trackMetastatusCode)) {
                    throw new IOException("Unexpected response code from video info: " + trackMetastatusCode);
            }
    JsonBrowser trackMeta = JsonBrowser.parse(trackMetaResponse.getEntity().getContent());
    String videoCid = trackMeta.get("data").get("cid").text();
    HttpGet request = new HttpGet("https://api.bilibili.com/x/player/playurl?bvid=" + trackInfo.identifier + "&cid=" + videoCid + "&qn=0&fnval=80&fnver=0&fourk=1");

    try (CloseableHttpResponse response = httpInterface.execute(request)) {
      int statusCode = response.getStatusLine().getStatusCode();
      if (!HttpClientTools.isSuccessWithContent(statusCode)) {
        throw new IOException("Unexpected status code from playback parameters page: " + statusCode);
      }

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