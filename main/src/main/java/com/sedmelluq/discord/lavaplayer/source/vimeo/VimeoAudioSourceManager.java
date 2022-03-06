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
import org.apache.http.HttpStatus;
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

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio source manager which detects Vimeo tracks by URL.
 */
public class VimeoAudioSourceManager implements AudioSourceManager, HttpConfigurable {
  private static final String TRACK_URL_REGEX = "^https://vimeo.com/[0-9]+(?:\\?.*|)$";
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
      return loadFromSearchPage(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
    }

    if (!trackUrlPattern.matcher(reference.identifier).matches()) {
      return null;
    }

    try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
      return loadFromTrackPage(httpInterface, reference.identifier);
    } catch (IOException e) {
      throw new FriendlyException("Loading Vimeo track information failed.", SUSPICIOUS, e);
    }
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

  private JsonBrowser loadSearchConfigFromPageContent(String content) throws IOException {
    String configText = DataFormatTools.extractBetween(content, "(vimeo.config || {}), ", ");");

    if (configText != null) {
      return JsonBrowser.parse(configText);
    }

    return null;
  }

  private AudioItem loadFromSearchPage(String query) {
    try (HttpInterface httpInterface = getHttpInterface()) {
      URI uri = new URIBuilder("https://vimeo.com/search")
      .addParameter("q", query).build();
      HttpGet get = new HttpGet(uri);
      get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.99 Safari/537.36");
      try (CloseableHttpResponse response = httpInterface.execute(get)) {
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode == HttpStatus.SC_NOT_FOUND) {
          return AudioReference.NO_TRACK;
        } else if (!HttpClientTools.isSuccessWithContent(statusCode)) {
          throw new FriendlyException("Server responded with an error.", SUSPICIOUS,
              new IllegalStateException("Response code is " + statusCode));
        }

        JsonBrowser result = loadSearchConfigFromPageContent(IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
        if (result == null) {
          return AudioReference.NO_TRACK;
        }

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
      }
    } catch (Exception e) {
      throw new FriendlyException("Failed to load vimeo search results.", SUSPICIOUS, e);
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
      clip.get("owner").get("name").safeText(),
      (long) (clip.get("duration").as(Double.class) * 1000.0),
      identifier,
      false,
      identifier,
      pictures.get(pictures.size() - 1).get("link").text()
    ), this);
  }

  public JsonBrowser loadConfigJsonFromPageContent(String content) throws IOException {
    String configText = DataFormatTools.extractBetween(content, "window.vimeo.clip_page_config = ", "\n");

    if (configText != null) {
      return JsonBrowser.parse(configText);
    }

    return null;
  }

  private AudioItem loadFromTrackPage(HttpInterface httpInterface, String trackUrl) throws IOException {
    try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(trackUrl))) {
      int statusCode = response.getStatusLine().getStatusCode();

      if (statusCode == HttpStatus.SC_NOT_FOUND) {
        return AudioReference.NO_TRACK;
      } else if (!HttpClientTools.isSuccessWithContent(statusCode)) {
        throw new FriendlyException("Server responded with an error.", SUSPICIOUS,
            new IllegalStateException("Response code is " + statusCode));
      }

      return loadTrackFromPageContent(trackUrl, IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
    }
  }

  private AudioTrack loadTrackFromPageContent(String trackUrl, String content) throws IOException {
    JsonBrowser config = loadConfigJsonFromPageContent(content);

    if (config == null) {
      throw new FriendlyException("Track information not found on the page.", SUSPICIOUS, null);
    }

    String artworkUrl = config.get("player").get("poster").get("url").text();
    if (artworkUrl == null || artworkUrl.isEmpty()) artworkUrl = config.get("thumbnail").get("src_2x").text();

    return new VimeoAudioTrack(new AudioTrackInfo(
        config.get("clip").get("title").text(),
        config.get("owner").get("display_name").text(),
        (long) (config.get("clip").get("duration").get("raw").as(Double.class) * 1000.0),
        trackUrl,
        false,
        trackUrl,
        artworkUrl
    ), this);
  }
}