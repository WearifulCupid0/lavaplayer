package com.sedmelluq.discord.lavaplayer.source.odysee;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.*;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class OdyseeAudioSourceManager implements AudioSourceManager, HttpConfigurable {
  private static final Logger log = LoggerFactory.getLogger(OdyseeAudioSourceManager.class);

  private static final String TRACK_URL_REGEX = "^(?:https?://|)odysee\\.com/(.+):.+/(.+):.+";
  private static final String SEARCH_PREFIX = "odsearch:";

  private static final Pattern TRACK_URL_PATTERN = Pattern.compile(TRACK_URL_REGEX);
  private static final Pattern SEARCH_PATTERN = Pattern.compile(SEARCH_PREFIX + "(.{3,99999})");

  private final HttpInterfaceManager httpInterfaceManager;
  private final boolean allowSearch;

  public OdyseeAudioSourceManager() {
    this(true);
  }

  public OdyseeAudioSourceManager(boolean allowSearch) {
    this.allowSearch = allowSearch;
    httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
  }

  @Override
  public String getSourceName() {
    return "odysee";
  }

  @Override
  public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
    Matcher trackMatcher = TRACK_URL_PATTERN.matcher(reference.identifier);

    if (trackMatcher.matches() && trackMatcher.groupCount() == 2) {
      return loadTrack(trackMatcher.group(1), trackMatcher.group(2));
    }

    if (this.allowSearch) {
      Matcher searchMatcher = SEARCH_PATTERN.matcher(reference.identifier);

      if (searchMatcher.matches()) {
        return loadSearchResult(searchMatcher.group(1));
      }
    }

    return null;
  }

  private AudioTrack loadTrack(String uploader, String videoName) {
    try (HttpInterface httpInterface = getHttpInterface()) {
      HttpPost post = new HttpPost(OdyseeConstants.API_URL);

      post.setEntity(new StringEntity(String.format(OdyseeConstants.RESOLVE_PAYLOAD, "lbry://" + uploader + "/" + videoName), ContentType.APPLICATION_JSON));

      try (CloseableHttpResponse response = httpInterface.execute(post)) {
        HttpClientTools.assertSuccessWithContent(response, "video api response");

        JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

        return extractTrackFromJson(json.get("result").get("lbry://" + uploader + "/" + videoName));
      }
    } catch (IOException e) {
      throw new FriendlyException("Error occurred when extracting video info.", SUSPICIOUS, e);
    }
  }

  private AudioTrack extractTrackFromJson(JsonBrowser json) throws IOException {
    if (!json.get("error").isNull()) throw new IOException("Error response from video info.");
    if (!json.get("value").get("stream_type").safeText().equals("video")) throw new IOException("Stream type is not video.");

    String durationStr = json.get("value").get("video").get("duration").text();
    long duration = durationStr == null ? Units.DURATION_MS_UNKNOWN : DataFormatTools.durationTextToMillis(durationStr);

    String name = json.get("name").safeText();
    String claimId = json.get("claim_id").safeText();
    String uploader = json.get("signing_channel").get("name").safeText();
    String thumbnail = json.get("value").get("thumbnail").get("url").safeText();

    return new OdyseeAudioTrack(new AudioTrackInfo(
        name,
        uploader,
        duration,
        name + "#" + claimId,
        false,
        getWatchUrl(uploader, name),
        thumbnail
    ), this);
  }

  private AudioItem loadSearchResult(String query) {
    try (
        HttpInterface httpInterface = getHttpInterface();
        CloseableHttpResponse response = httpInterface.execute(new HttpGet(buildSearchUri(query)))
    ) {
      return loadSearchResultsFromResponse(response, query);
    } catch (IOException e) {
      throw new FriendlyException("Loading search results from Odysee failed.", SUSPICIOUS, e);
    }
  }

  private AudioItem loadSearchResultsFromResponse(HttpResponse response, String query) throws IOException {
    try {
      JsonBrowser searchResults = JsonBrowser.parse(response.getEntity().getContent());
      return extractTracksFromSearchResults(query, searchResults);
    } finally {
      EntityUtils.consumeQuietly(response.getEntity());
    }
  }

  private AudioItem extractTracksFromSearchResults(String query, JsonBrowser searchResults) {
    List<String> urls = new ArrayList<>();

    for (JsonBrowser item : searchResults.values()) {
      urls.add(String.format("%s#%s", item.get("name").safeText(), item.get("claimId").safeText()));
    }

    return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", loadTracksFromSearchResult(urls), null, true);
  }

  private List<AudioTrack> loadTracksFromSearchResult(List<String> urls) {
    try (HttpInterface httpInterface = getHttpInterface()) {
      HttpPost post = new HttpPost(OdyseeConstants.API_URL);

      post.setEntity(new StringEntity(String.format(OdyseeConstants.RESOLVE_PAYLOAD, String.join("\", \"", urls)), ContentType.APPLICATION_JSON));

      try (CloseableHttpResponse response = httpInterface.execute(post)) {
        HttpClientTools.assertSuccessWithContent(response, "search api response");

        List<AudioTrack> tracks = new ArrayList<>();

        JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent()).get("result");

        for (String url : urls) {
          JsonBrowser trackInfo = json.get(url);

          try {
            tracks.add(extractTrackFromJson(trackInfo));
          } catch (IOException e) {
            log.debug("Exception when searching for Odysee track, skipping. (Error: {})", e.getMessage());
          }
        }

        return tracks;
      }
    } catch (IOException e) {
      throw new FriendlyException("Loading track from search result from Odysee failed.", SUSPICIOUS, e);
    }
  }

  private URI buildSearchUri(String query) {
    try {
      return new URIBuilder(OdyseeConstants.SEARCH_URL)
          .addParameter("s", query)
          .addParameter("size", "20")
          .build();
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean isTrackEncodable(AudioTrack track) {
    return true;
  }

  @Override
  public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
    // nothing to encode
  }

  @Override
  public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
    return new OdyseeAudioTrack(trackInfo, this);
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

  private static String getWatchUrl(String uploader, String videoName) {
    return "https://odysee.com/" + uploader + "/" + videoName;
  }
}