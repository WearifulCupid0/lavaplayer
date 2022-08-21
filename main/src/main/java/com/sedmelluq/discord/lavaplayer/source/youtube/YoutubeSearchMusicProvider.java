package com.sedmelluq.discord.lavaplayer.source.youtube;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.PBJUtils;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.MUSIC_SEARCH_URL;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.MUSIC_SEARCH_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.MUSIC_WATCH_URL;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Handles processing YouTube Music searches.
 */
public class YoutubeSearchMusicProvider implements YoutubeSearchMusicResultLoader {
  private static final Logger log = LoggerFactory.getLogger(YoutubeSearchMusicProvider.class);

  private final HttpInterfaceManager httpInterfaceManager;

  public YoutubeSearchMusicProvider() {
    this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
  }

  public ExtendedHttpConfigurable getHttpConfiguration() {
    return httpInterfaceManager;
  }

  /**
   * @param query Search query.
   * @return Playlist of the first page of music results.
   */
  @Override
  public AudioItem loadSearchMusicResult(String query, Function<AudioTrackInfo, AudioTrack> trackFactory) {
    String escapedQuery = query.replaceAll("\"|\\\\", "");
    log.debug("Performing a search music with query {}", escapedQuery);

    try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
      HttpPost post = new HttpPost(MUSIC_SEARCH_URL);
      StringEntity payload = new StringEntity(String.format(MUSIC_SEARCH_PAYLOAD, escapedQuery), "UTF-8");
      post.setHeader("Referer", "music.youtube.com");
      post.setEntity(payload);

      try (CloseableHttpResponse response = httpInterface.execute(post)) {
        HttpClientTools.assertSuccessWithContent(response, "search music response");

        String responseText = EntityUtils.toString(response.getEntity(), UTF_8);

        JsonBrowser jsonBrowser = JsonBrowser.parse(responseText);
        return extractSearchResults(jsonBrowser, query, trackFactory);
      }
    } catch (Exception e) {
      throw ExceptionTools.wrapUnfriendlyExceptions(e);
    }
  }

  private AudioItem extractSearchResults(JsonBrowser jsonBrowser, String query,
                                         Function<AudioTrackInfo, AudioTrack> trackFactory) {
    List<AudioTrack> tracks;
    log.debug("Attempting to parse results from music search page");
    try {
      tracks = extractMusicSearchPage(jsonBrowser, trackFactory);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (tracks.isEmpty()) {
      return AudioReference.NO_TRACK;
    } else {
      return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
    }
  }

  private List<AudioTrack> extractMusicSearchPage(JsonBrowser jsonBrowser, Function<AudioTrackInfo, AudioTrack> trackFactory) throws IOException {
    ArrayList<AudioTrack> list = new ArrayList<>();
    JsonBrowser tracks = jsonBrowser.get("contents")
        .get("tabbedSearchResultsRenderer")
        .get("tabs")
        .index(0)
        .get("tabRenderer")
        .get("content")
        .get("sectionListRenderer")
        .get("contents")
        .index(0)
        .get("musicShelfRenderer")
        .get("contents");
    if (tracks.isNull()) {
      tracks = jsonBrowser.get("contents")
          .get("tabbedSearchResultsRenderer")
          .get("tabs")
          .index(0)
          .get("tabRenderer")
          .get("content")
          .get("sectionListRenderer")
          .get("contents")
          .index(1)
          .get("musicShelfRenderer")
          .get("contents");
    }
    tracks.values().forEach(jsonTrack -> {
      AudioTrack track = extractMusicTrack(jsonTrack, trackFactory);
      if (track != null) list.add(track);
    });
    return list;
  }

  private AudioTrack extractMusicTrack(JsonBrowser jsonBrowser, Function<AudioTrackInfo, AudioTrack> trackFactory) {
    JsonBrowser renderer = jsonBrowser.get("musicResponsiveListItemRenderer");
    JsonBrowser columns = renderer.get("flexColumns");
    if (columns.isNull()) {
      // Somehow don't get track info, ignore
      return null;
    }
    JsonBrowser thumbnail = renderer.get("thumbnail").get("musicThumbnailRenderer");
    JsonBrowser firstColumn = columns.index(0)
        .get("musicResponsiveListItemFlexColumnRenderer")
        .get("text")
        .get("runs")
        .index(0);
    String title = firstColumn.get("text").text();
    String videoId = firstColumn.get("navigationEndpoint")
        .get("watchEndpoint")
        .get("videoId").text();
    if (videoId == null) {
      // If track is not available on YouTube Music videoId will be empty
      return null;
    }
    List<JsonBrowser> secondColumn = columns.index(1)
        .get("musicResponsiveListItemFlexColumnRenderer")
        .get("text")
        .get("runs").values();
    String author = secondColumn.get(0)
        .get("text").text();
    JsonBrowser lastElement = secondColumn.get(secondColumn.size() - 1);

    if (!lastElement.get("navigationEndpoint").isNull()) {
      // The duration element should not have this key, if it does, then duration is probably missing, so return
      return null;
    }
    List<JsonBrowser> badges = renderer.get("badges").values();
    boolean explicit = false;
    for (JsonBrowser badge : badges) {
      explicit = badge.get("musicInlineBadgeRenderer").get("icon").get("iconType").safeText().equals("MUSIC_EXPLICIT_BADGE");
    }

    long duration = DataFormatTools.durationTextToMillis(lastElement.get("text").text());

    AudioTrackInfo info = new AudioTrackInfo(title, author, duration, videoId, false,
        MUSIC_WATCH_URL + videoId, PBJUtils.getYouTubeMusicThumbnail(thumbnail, videoId), explicit);

    return trackFactory.apply(info);
  }
}