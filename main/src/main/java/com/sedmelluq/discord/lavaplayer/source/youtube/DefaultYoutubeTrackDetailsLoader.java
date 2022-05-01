package com.sedmelluq.discord.lavaplayer.source.youtube;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;

import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.YOUTUBE_ORIGIN;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.PLAYER_EMBED_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.PLAYER_TRAILER_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.PLAYER_TVHTML5_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.PLAYER_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.PLAYER_URL;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.ATTRIBUTE_VERIFY_AGE;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.VERIFY_AGE_URL;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.VERIFY_AGE_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.tools.ExceptionTools.throwWithDebugInfo;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;
import static java.nio.charset.StandardCharsets.UTF_8;

public class DefaultYoutubeTrackDetailsLoader implements YoutubeTrackDetailsLoader {
  private static final Logger log = LoggerFactory.getLogger(DefaultYoutubeTrackDetailsLoader.class);

  private volatile CachedPlayerScript cachedPlayerScript = null;

  @Override
  public YoutubeTrackDetails loadDetails(HttpInterface httpInterface, String videoId, boolean requireFormats, YoutubeAudioSourceManager sourceManager) {
    try {
      return load(httpInterface, videoId, requireFormats, sourceManager);
    } catch (IOException e) {
      throw ExceptionTools.toRuntimeException(e);
    }
  }

  private YoutubeTrackDetails load(
      HttpInterface httpInterface,
      String videoId,
      boolean requireFormats,
      YoutubeAudioSourceManager sourceManager
  ) throws IOException {
    JsonBrowser mainInfo = loadTrackInfoFromInnertube(httpInterface, videoId, sourceManager, null);

    try {
      YoutubeTrackJsonData initialData = loadBaseResponse(mainInfo, httpInterface, videoId, sourceManager);

      if (initialData == null) {
        return null;
      }

      YoutubeTrackJsonData finalData = augmentWithPlayerScript(initialData, httpInterface, videoId, requireFormats);
      return new DefaultYoutubeTrackDetails(videoId, finalData);
    } catch (FriendlyException e) {
      throw e;
    } catch (Exception e) {
      throw throwWithDebugInfo(log, e, "Error when extracting data", "mainJson", mainInfo.format());
    }
  }

  protected YoutubeTrackJsonData loadBaseResponse(
      JsonBrowser mainInfo,
      HttpInterface httpInterface,
      String videoId,
      YoutubeAudioSourceManager sourceManager
  ) throws IOException {
    YoutubeTrackJsonData data = YoutubeTrackJsonData.fromMainResult(mainInfo);
    InfoStatus status = checkPlayabilityStatus(data.playerResponse, false, false);

    if (status == InfoStatus.DOES_NOT_EXIST) {
      return null;
    }

    if (status == InfoStatus.PREMIERE_TRAILER) {
      JsonBrowser trackInfo = loadTrackInfoFromInnertube(httpInterface, videoId, sourceManager, status);
      data = YoutubeTrackJsonData.fromMainResult(trackInfo
          .get("playabilityStatus")
          .get("errorScreen")
          .get("ypcTrailerRenderer")
          .get("unserializedPlayerResponse")
      );
      status = checkPlayabilityStatus(data.playerResponse, true, false);
      
    }

    if (status == InfoStatus.REQUIRES_LOGIN) {
      JsonBrowser trackInfo = loadTrackInfoFromInnertube(httpInterface, videoId, sourceManager, status);
      data = YoutubeTrackJsonData.fromMainResult(trackInfo);
      status = checkPlayabilityStatus(data.playerResponse, true, false);
    }

    if (status == InfoStatus.NON_EMBEDDABLE) {
      JsonBrowser trackInfo = loadTrackInfoFromInnertube(httpInterface, videoId, sourceManager, status);
      checkPlayabilityStatus(trackInfo, true, false);
      data = YoutubeTrackJsonData.fromMainResult(trackInfo);
    }

    if (status == InfoStatus.CONTENT_CHECK_REQUIRED) {
      JsonBrowser response = fetchContentCheck(videoId, httpInterface);
      if (response == null || response.isNull() || response.get("actions").index(0).isNull()) {
        throw new FriendlyException("YouTube send an empty content check response.", SUSPICIOUS, null);
      }
      JsonBrowser playerResponse = fetchResponseFromContentCheck(response.get("actions").index(0), sourceManager);
      if (playerResponse == null || playerResponse.isNull() || playerResponse.index(0).isNull()) {
        throw new FriendlyException("YouTube send an empty content check player response.", SUSPICIOUS, null);
      }
      JsonBrowser trackInfo = findPlayerResponseFromContentCheck(playerResponse);
      if (trackInfo == null || trackInfo.isNull()) {
        throw new FriendlyException("Failed to find track info from YouTube content check player response.", SUSPICIOUS, null);
      }
      checkPlayabilityStatus(trackInfo, true, true);
      data = YoutubeTrackJsonData.fromMainResult(trackInfo);
    }

    return data;
  }

  protected InfoStatus checkPlayabilityStatus(JsonBrowser playerResponse, boolean secondCheck, boolean fromContentCheck) {
    JsonBrowser statusBlock = playerResponse.get("playabilityStatus");

    if (statusBlock.isNull()) {
      throw new RuntimeException("No playability status block.");
    }

    String status = statusBlock.get("status").text();

    if (status == null) {
      throw new RuntimeException("No playability status field.");
    } else if (status.contains("OK")) {
      return InfoStatus.INFO_PRESENT;
    } else if (status.contains("ERROR")) {
      String reason = statusBlock.get("reason").text();

      if (reason.contains("This video is unavailable")) {
        return InfoStatus.DOES_NOT_EXIST;
      } else {
        throw new FriendlyException(reason, COMMON, null);
      }
    } else if (status.contains("UNPLAYABLE")) {
      String unplayableReason = getUnplayableReason(statusBlock);

      if (unplayableReason.contains("Playback on other websites has been disabled by the video owner")) {
        return InfoStatus.NON_EMBEDDABLE;
      }

      throw new FriendlyException(unplayableReason, COMMON, null);
    } else if (status.contains("LOGIN_REQUIRED")) {
      String errorReason = statusBlock.get("reason").text();

      if (errorReason.contains("This video is private")) {
        throw new FriendlyException("This is a private video.", COMMON, null);
      }

      if (errorReason.contains("This video may be inappropriate for some users") && secondCheck) {
        throw new FriendlyException("This video requires age verification.", SUSPICIOUS,
                new IllegalStateException("You did not set email and password in YoutubeAudioSourceManager."));
      }

      return InfoStatus.REQUIRES_LOGIN;
    } else if (status.contains("CONTENT_CHECK_REQUIRED")) {
      if (fromContentCheck) {
        throw new FriendlyException("This video requires age verification.", SUSPICIOUS,
                new IllegalStateException("You did not set email and password in YoutubeAudioSourceManager."));
      }
      return InfoStatus.CONTENT_CHECK_REQUIRED;
    } else if (status.contains("LIVE_STREAM_OFFLINE")) {
      if (!statusBlock.get("errorScreen").get("ypcTrailerRenderer").isNull()) {
        return InfoStatus.PREMIERE_TRAILER;
      }

      throw new FriendlyException(getUnplayableReason(statusBlock), COMMON, null);
    } else {
      throw new FriendlyException("This video cannot be viewed anonymously.", COMMON, null);
    }
  }

  protected enum InfoStatus {
    INFO_PRESENT,
    REQUIRES_LOGIN,
    DOES_NOT_EXIST,
    LIVE_STREAM_OFFLINE,
    PREMIERE_TRAILER,
    NON_EMBEDDABLE,
    CONTENT_CHECK_REQUIRED
  }

  protected String getUnplayableReason(JsonBrowser statusBlock) {
    JsonBrowser playerErrorMessage = statusBlock.get("errorScreen").get("playerErrorMessageRenderer");
    String unplayableReason = statusBlock.get("reason").text();

    if (!playerErrorMessage.get("subreason").isNull()) {
      JsonBrowser subreason = playerErrorMessage.get("subreason");

      if (!subreason.get("simpleText").isNull()) {
        unplayableReason = subreason.get("simpleText").text();
      } else if (!subreason.get("runs").isNull() && subreason.get("runs").isList()) {
        StringBuilder reasonBuilder = new StringBuilder();
        subreason.get("runs").values().forEach(
            item -> reasonBuilder.append(item.get("text").text()).append('\n')
        );
        unplayableReason = reasonBuilder.toString();
      }
    }

    return unplayableReason;
  }

  protected JsonBrowser loadTrackInfoFromInnertube(
          HttpInterface httpInterface,
          String videoId,
          YoutubeAudioSourceManager sourceManager,
          InfoStatus infoStatus
  ) throws IOException {
    if (cachedPlayerScript == null) fetchScript(videoId, httpInterface);

    YoutubeSignatureCipher playerScriptTimestamp = sourceManager.getSignatureResolver().getExtractedScript(
            httpInterface,
            cachedPlayerScript.playerScriptUrl
    );
    HttpPost post = new HttpPost(PLAYER_URL);
    StringEntity payload;

    if (infoStatus == InfoStatus.PREMIERE_TRAILER) {
      // Android client gives encoded Base64 response to trailer which is also protobuf so we can't decode it
      payload = new StringEntity(String.format(PLAYER_TRAILER_PAYLOAD, videoId, playerScriptTimestamp.scriptTimestamp), "UTF-8");
    } else if (infoStatus == InfoStatus.NON_EMBEDDABLE) {
      // Used when age restriction bypass failed, if we have valid auth then most likely this request will be successful
      payload = new StringEntity(String.format(PLAYER_PAYLOAD, videoId, playerScriptTimestamp.scriptTimestamp), "UTF-8");
    } else if (infoStatus == InfoStatus.REQUIRES_LOGIN) {
      // Age restriction bypass
      payload = new StringEntity(String.format(PLAYER_TVHTML5_PAYLOAD, videoId, playerScriptTimestamp.scriptTimestamp), "UTF-8");
    } else {
      // Default payload from what we start trying to get required data
      payload = new StringEntity(String.format(PLAYER_EMBED_PAYLOAD, videoId, playerScriptTimestamp.scriptTimestamp), "UTF-8");
    }

    post.setEntity(payload);
    try (CloseableHttpResponse response = httpInterface.execute(post)) {
      HttpClientTools.assertSuccessWithContent(response, "video page response");

        String responseText = EntityUtils.toString(response.getEntity(), UTF_8);

        try {
          return JsonBrowser.parse(responseText);
        } catch (FriendlyException e) {
          throw e;
        } catch (Exception e) {
          throw new FriendlyException("Received unexpected response from YouTube.", SUSPICIOUS,
              new RuntimeException("Failed to parse: " + responseText, e));
        }
    }
  }

  protected YoutubeTrackJsonData augmentWithPlayerScript(
          YoutubeTrackJsonData data,
          HttpInterface httpInterface,
          String videoId,
          boolean requireFormats
  ) throws IOException {
    long now = System.currentTimeMillis();

    if (data.playerScriptUrl != null) {
      cachedPlayerScript = new CachedPlayerScript(data.playerScriptUrl, now);
      return data;
    } else if (!requireFormats) {
      return data;
    }

    CachedPlayerScript cached = cachedPlayerScript;

    if (cached != null && cached.timestamp + 600000L >= now) {
      return data.withPlayerScriptUrl(cached.playerScriptUrl);
    }

    return data.withPlayerScriptUrl(fetchScript(videoId, httpInterface));
  }

  private String fetchScript(String videoId, HttpInterface httpInterface) throws IOException {
    long now = System.currentTimeMillis();

    try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://www.youtube.com/embed/" + videoId))) {
      HttpClientTools.assertSuccessWithContent(response, "youtube embed video id");

      String responseText = EntityUtils.toString(response.getEntity());
      String encodedUrl = DataFormatTools.extractBetween(responseText, "\"jsUrl\":\"", "\"");

      if (encodedUrl == null) {
        throw throwWithDebugInfo(log, null, "no jsUrl found", "html", responseText);
      }

      String fetchedPlayerScript = JsonBrowser.parse("{\"url\":\"" + encodedUrl + "\"}").get("url").text();
      cachedPlayerScript = new CachedPlayerScript(fetchedPlayerScript, now);

      return fetchedPlayerScript;
    }
  }

  private JsonBrowser fetchContentCheck(String videoId, HttpInterface httpInterface) throws IOException {
    HttpPost post = new HttpPost(VERIFY_AGE_URL);
    post.setEntity(new StringEntity(String.format(VERIFY_AGE_PAYLOAD, videoId)));
    try (CloseableHttpResponse response = httpInterface.execute(post)) {
      HttpClientTools.assertSuccessWithContent(response, "youtube verify age response");

      String responseText = EntityUtils.toString(response.getEntity(), UTF_8);
      try {
        return JsonBrowser.parse(responseText);
      } catch (FriendlyException e) {
        throw e;
      } catch (Exception e) {
        throw new FriendlyException("Received unexpected response from YouTube.", SUSPICIOUS,
            new RuntimeException("Failed to parse: " + responseText, e));
      }
    }
  }

  private JsonBrowser fetchResponseFromContentCheck(JsonBrowser action, YoutubeAudioSourceManager sourceManager) throws IOException {
    try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
      httpInterface.getContext().setAttribute(ATTRIBUTE_VERIFY_AGE, Boolean.TRUE);
      JsonBrowser endpointJson = action.get("navigateAction").get("endpoint");
      String endpoint = endpointJson.get("urlEndpoint").get("url").text();
      if (endpoint == null || endpoint.isEmpty()) {
        throw new IOException("Watch endpoint not found on verify age response.");
      }
      HttpPost post = new HttpPost(YOUTUBE_ORIGIN + endpoint);
      post.setHeader("Content-Type", "application/x-www-form-urlencoded");
      post.setHeader("Authorization", "Bearer " + sourceManager.getAccessTokenTracker().getAccessToken());
      post.setEntity(new StringEntity("command=" + URLEncoder.encode(endpointJson.toString(), UTF_8.toString())));
      try (CloseableHttpResponse response = httpInterface.execute(post)) {
        HttpClientTools.assertSuccessWithContent(response, "youtube verify age player response");

        String responseText = EntityUtils.toString(response.getEntity(), UTF_8);
        try {
          return JsonBrowser.parse(responseText);
        } catch (FriendlyException e) {
          throw e;
        } catch (Exception e) {
          throw new FriendlyException("Received unexpected response from YouTube.", SUSPICIOUS,
              new RuntimeException("Failed to parse: " + responseText, e));
        }
      }
    }
  }

  private JsonBrowser findPlayerResponseFromContentCheck(JsonBrowser response) {
    JsonBrowser playerResponse = null;
    for (JsonBrowser value : response.values()) {
      if (!value.get("playerResponse").isNull()) {
        playerResponse = value.get("playerResponse");
        break;
      }
    }
    return playerResponse;
  }

  public CachedPlayerScript getCachedPlayerScript() {
    return cachedPlayerScript;
  }

  public void clearCache() {
    cachedPlayerScript = null;
  }

  public static class CachedPlayerScript {
    public final String playerScriptUrl;
    public final long timestamp;

    public CachedPlayerScript(String playerScriptUrl, long timestamp) {
      this.playerScriptUrl = playerScriptUrl;
      this.timestamp = timestamp;
    }

    public String getPlayerScriptUrl() {
      return playerScriptUrl;
    }
  }
}