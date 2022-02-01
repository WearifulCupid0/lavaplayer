package com.sedmelluq.discord.lavaplayer.source.reddit;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class RedditAudioSourceManager implements AudioSourceManager, HttpConfigurable {
  private static final String URL_REGEX = "^https?://(?:old\\.|www\\.)?reddit.com/r/\\w+/\\w+/(.+)/.+";
  private static final String VIDEO_URL_REGEX = "^https?://v\\.redd\\.it/(.+)/.+";

  private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);
  private static final Pattern VIDEO_URL_PATTERN = Pattern.compile(VIDEO_URL_REGEX);

  private static final String API_URL = "https://api.reddit.com/api/info/?id=t3_";

  private final HttpInterfaceManager httpInterfaceManager;

  public RedditAudioSourceManager() {
    this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    this.httpInterfaceManager.setHttpContextFilter(new RedditHttpContextFilter());
  }

  @Override
  public String getSourceName() {
    return "reddit";
  }

  @Override
  public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
    Matcher urlMatcher = URL_PATTERN.matcher(reference.identifier);

    if (urlMatcher.matches()) {
      return loadTrack(urlMatcher.group(1));
    }

    return null;
  }

  private AudioTrack loadTrack(String id) {
    try (HttpInterface httpInterface = getHttpInterface()) {
      try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(API_URL + id))) {
        int statusCode = response.getStatusLine().getStatusCode();

        if (!HttpClientTools.isSuccessWithContent(statusCode)) {
          throw new IOException("Unexpected response code from video info: " + statusCode);
        }

        JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

        return extractTrackFromJson(json);
      }
    } catch (IOException e) {
      throw new FriendlyException(
          "Error occurred when extracting video info.",
          e.getMessage().equals("Reddit post does not contain a video.") ? COMMON : SUSPICIOUS,
          e
      );
    }
  }

  private AudioTrack extractTrackFromJson(JsonBrowser json) throws IOException {
    JsonBrowser data = json.get("data").get("children").index(0).get("data");

    if (!data.get("post_hint").text().equals("hosted:video")) throw new IOException("Reddit post does not contain a video.");

    String title = data.get("title").safeText();
    String author = data.get("author").safeText();
    String thumbnailUrl = data.get("thumbnail").safeText();

    JsonBrowser videoData = data.get("secure_media").get("reddit_video");

    String url = videoData.get("fallback_url").text();

    Matcher matcher = VIDEO_URL_PATTERN.matcher(url);

    if (!matcher.matches())
      throw new IOException("Couldn't get playback url.");

    String id = matcher.group(1);
    long duration = DataFormatTools.durationTextToMillis(videoData.get("duration").text());

    return new RedditAudioTrack(new AudioTrackInfo(
        title,
        author,
        duration,
        id,
        false,
        url,
        thumbnailUrl
    ), this);
  }

  @Override
  public boolean isTrackEncodable(AudioTrack track) {
    return true;
  }

  @Override
  public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
    // nothing to encode here
  }

  @Override
  public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
    return new RedditAudioTrack(trackInfo, this);
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
}