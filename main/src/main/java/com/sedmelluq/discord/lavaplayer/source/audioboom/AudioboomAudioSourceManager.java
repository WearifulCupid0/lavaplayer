package com.sedmelluq.discord.lavaplayer.source.audioboom;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.ThreadLocalHttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio source manager which detects audioboom.com tracks by URL.
 */
public class AudioboomAudioSourceManager implements HttpConfigurable, AudioSourceManager {
  private static final String AUDIOBOOM_URL = "https://audioboom.com/posts/";
  private static final String AUDIOBOOM_REGEX = "^(?:http://|https://|)?(?:www\\.)?audioboom\\.com/posts/([a-zA-Z0-9-_]+)";
  private static final Pattern audioboomPattern = Pattern.compile(AUDIOBOOM_REGEX);

  private final HttpInterfaceManager httpInterfaceManager;

  public AudioboomAudioSourceManager() {
    httpInterfaceManager = new ThreadLocalHttpInterfaceManager(
        HttpClientTools
            .createSharedCookiesHttpBuilder()
            .setRedirectStrategy(new HttpClientTools.NoRedirectsStrategy()),
        HttpClientTools.DEFAULT_REQUEST_CONFIG
    );
  }

  @Override
  public String getSourceName() {
    return "audioboom";
  }

  @Override
  public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
    Matcher m = audioboomPattern.matcher(reference.identifier);

    if (m.find()) return extractAudioFromPage(m.group(1));

    return null;
  }

  @Override
  public boolean isTrackEncodable(AudioTrack track) {
    return true;
  }

  @Override
  public void encodeTrack(AudioTrack track, DataOutput output) {
    // No custom values that need saving
  }

  @Override
  public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
    return new AudioboomAudioTrack(trackInfo, this);
  }

  @Override
  public void shutdown() {
    // Nothing to shut down
  }

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

  private AudioTrack extractAudioFromPage(String path) {
    String url = AUDIOBOOM_URL + path;
    try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(URI.create(url)))) {
      HttpClientTools.assertSuccessWithContent(response, "audio page");

      String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
      Document document = Jsoup.parse(html);

      AudioTrackInfo trackInfo = new AudioTrackInfo(
        document.selectFirst("meta[property=og:title]").attr("content"),
        document.selectFirst("meta[property=og:audio:artist]").attr("content"),
        Units.DURATION_MS_UNKNOWN,
        url,
        false,
        url,
        document.selectFirst("meta[property=og:image]").attr("content")
      );

      return new AudioboomAudioTrack(trackInfo, this);
    } catch (IOException e) {
      throw new FriendlyException("Failed to load info for yarn clip", SUSPICIOUS, null);
    }
  }
}
