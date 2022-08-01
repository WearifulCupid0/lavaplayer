package com.sedmelluq.discord.lavaplayer.source.vimeo;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio source manager which detects Vimeo tracks by URL.
 */
public class VimeoAudioSourceManager implements AudioSourceManager, HttpConfigurable {
  public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.99 Safari/537.36";

  private static final String TRACK_URL_REGEX = "^(?:http://|https://|)(?:www\\.|)vimeo.com/(\\d+)";
  private static final Pattern trackUrlPattern = Pattern.compile(TRACK_URL_REGEX);
  private static final String SEARCH_PREFIX = "vmsearch:";

  private final boolean allowSearch;

  private final HttpInterfaceManager httpInterfaceManager;

  /**
   * Create an instance.
   */
  public VimeoAudioSourceManager() {
    this(true);
  }

  public VimeoAudioSourceManager(boolean allowSearch) {
    this.allowSearch = allowSearch;

    httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
  }

  @Override
  public String getSourceName() {
    return "vimeo";
  }

  @Override
  public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
    if (allowSearch && reference.identifier.startsWith(SEARCH_PREFIX)) {
      return loadSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
    }

    Matcher m = trackUrlPattern.matcher(reference.identifier);
    if (m.find()) return loadTrack(m.group(1));
    
    return null;
  }

  @Override
  public boolean isTrackEncodable(AudioTrack track) {
    return true;
  }

  @Override
  public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
    // Nothing special to encode
  }

  @Override
  public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
    return new VimeoAudioTrack(trackInfo, this);
  }

  @Override
  public void shutdown() {
    ExceptionTools.closeWithWarnings(httpInterfaceManager);
  }

  /**
   * @return Get an HTTP interface for a playing track.
   */
  public HttpInterface getHttpInterface() {
    return httpInterfaceManager.getInterface();
  }

  @Override
  public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
    httpInterfaceManager.configureRequests(configurator);
  }

  @Override
  public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
    httpInterfaceManager.configureBuilder(configurator);
  }

  public JsonBrowser requestPage(URI uri, String input1, String input2) {
    try (HttpInterface httpInterface = getHttpInterface()) {
      HttpGet get = new HttpGet(uri);
      get.addHeader("User-Agent", USER_AGENT);
      get.addHeader("Host", "vimeo.com");
      get.addHeader("Accept", "*/*");
      try (CloseableHttpResponse response = httpInterface.execute(get)) {
        HttpClientTools.assertSuccessWithContent(response, "response page");
        String text = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
        String extracted = DataFormatTools.extractBetween(text, input1, input2);
        if (extracted == null) {
          throw new IOException("Extract points not found on vimeo page.");
        }

        return JsonBrowser.parse(extracted);
      }
    } catch (IOException e) {
      throw new FriendlyException("Failed to load vimeo page.", SUSPICIOUS, e);
    }
  }

  private AudioItem loadSearch(String query) {
    try {
      URI uri = new URIBuilder("https://vimeo.com/search").addParameter("q", query).build();
      JsonBrowser result = requestPage(uri, "(vimeo.config || {}), ", ");");

      List<JsonBrowser> clips = result.get("api").get("initial_json").get("data").values();
      List<AudioTrack> tracks = new ArrayList<>();
      clips.forEach(data -> {
        AudioTrack track = loadSearchTrack(data);
        if (track != null) tracks.add(track);
      });

      if (tracks.size() < 1) {
        return AudioReference.NO_TRACK;
      }

      return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
    } catch (Exception e) {
      throw new FriendlyException("Failed to load vimeo search results", SUSPICIOUS, e);
    }
  }

  private AudioTrack loadSearchTrack(JsonBrowser content) {
    JsonBrowser clip = content.get("clip");

    if (clip.isNull()) {
      return null;
    }

    String identifier = clip.get("link").text();
    List<JsonBrowser> pictures = clip.get("pictures").get("sizes").values();

    return new VimeoAudioTrack(new AudioTrackInfo(
      clip.get("name").safeText(),
      clip.get("user").get("name").safeText(),
      (long) (clip.get("duration").as(Double.class) * 1000.0),
      identifier,
      false,
      identifier,
      pictures.get(pictures.size() - 1).get("link").text()
    ), this);
  }

  private AudioItem loadTrack(String identifier) {
    String trackUrl = "https://vimeo.com/" + identifier;
    JsonBrowser config = requestPage(URI.create(trackUrl), "window.vimeo.clip_page_config = ", "\n");

    if (config.get("clip").isNull() || config.get("player").isNull()) {
      return AudioReference.NO_TRACK;
    }

    String artworkUrl = config.get("player").get("poster").get("url").text();
    if (artworkUrl == null || artworkUrl.isEmpty()) artworkUrl = config.get("thumbnail").get("src_2x").text();
    String contentRating = config.get("content_rating").get("type").text();

    return new VimeoAudioTrack(new AudioTrackInfo(
        config.get("clip").get("title").text(),
        config.get("owner").get("display_name").text(),
        (long) (config.get("clip").get("duration").get("raw").as(Double.class) * 1000.0),
        trackUrl,
        false,
        trackUrl,
        artworkUrl,
        contentRating != null && !contentRating.equals("unrated")
    ), this);
  }
}