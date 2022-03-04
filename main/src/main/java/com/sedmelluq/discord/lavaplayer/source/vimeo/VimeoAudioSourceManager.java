package com.sedmelluq.discord.lavaplayer.source.vimeo;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
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

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio source manager which detects Vimeo tracks by URL.
 */
public class VimeoAudioSourceManager implements AudioSourceManager, HttpConfigurable {
  private static final String PLAYER_URL = "https://player.vimeo.com/video/%s/config";
  private static final String TRACK_URL_REGEX = "^(?:http://|https://|)(?:www\\.|player\\.|)vimeo\\.com/(?:video/|)(\\d+)";
  private static final Pattern trackUrlPattern = Pattern.compile(TRACK_URL_REGEX);

  private final HttpInterfaceManager httpInterfaceManager;

  /**
   * Create an instance.
   */
  public VimeoAudioSourceManager() {
    httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
  }

  @Override
  public String getSourceName() {
    return "vimeo";
  }

  @Override
  public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
    Matcher matcher = trackUrlPattern.matcher(reference.identifier);

    if (matcher.find()) {
      JsonBrowser config = fetchPlayerConfig(matcher.group(1));
      if (config != null) return buildTrack(config);
    }
    
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

  public JsonBrowser fetchPlayerConfig(String id) {
    try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(String.format(PLAYER_URL, id)))) {
      HttpClientTools.assertSuccessWithContent(response, "player config");

      return JsonBrowser.parse(response.getEntity().getContent());
    } catch (IOException e) {
      throw new FriendlyException("Failed to fetch vimeo player config", SUSPICIOUS, e);
    }
  }

  public AudioTrack buildTrack(JsonBrowser json) {
    JsonBrowser video = json.get("video");

    AudioTrackInfo trackInfo = new AudioTrackInfo(
      video.get("title").text(),
      video.get("owner").get("name").text(),
      (long) (video.get("duration").as(Long.class) * 1000.0),
      video.get("id").text(),
      false,
      video.get("url").text(),
      video.get("thumbs").get("1028").text()
    );

    return new VimeoAudioTrack(trackInfo, this);
  }
}
