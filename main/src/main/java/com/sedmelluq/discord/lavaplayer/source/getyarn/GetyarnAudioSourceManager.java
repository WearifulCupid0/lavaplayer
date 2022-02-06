package com.sedmelluq.discord.lavaplayer.source.getyarn;

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
 * Audio source manager which detects getyarn.io tracks by URL.
 */
public class GetyarnAudioSourceManager implements HttpConfigurable, AudioSourceManager {
  private static final Pattern GETYARN_REGEX = Pattern.compile("(?:http://|https://(?:www\\.)?)?getyarn\\.io/yarn-clip/(.*)");

  private final HttpInterfaceManager httpInterfaceManager;

  public GetyarnAudioSourceManager() {
    httpInterfaceManager = new ThreadLocalHttpInterfaceManager(
        HttpClientTools
            .createSharedCookiesHttpBuilder()
            .setRedirectStrategy(new HttpClientTools.NoRedirectsStrategy()),
        HttpClientTools.DEFAULT_REQUEST_CONFIG
    );
  }

  @Override
  public String getSourceName() {
    return "getyarn.io";
  }

  @Override
  public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
    final Matcher m = GETYARN_REGEX.matcher(reference.identifier);

    if (!m.matches()) {
      return null;
    }

    return extractVideoUrlFromPage(reference);
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
    return new GetyarnAudioTrack(trackInfo, this);
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

  private AudioTrack extractVideoUrlFromPage(AudioReference reference) {
    try (final CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(reference.identifier))) {
      final String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
      final Document document = Jsoup.parse(html);
      
      final AudioTrackInfo trackInfo = new AudioTrackInfo(
        document.selectFirst("meta[property=og:title]").attr("content"),
        "Unknown author",
        Units.DURATION_MS_UNKNOWN,
        document.selectFirst("meta[property=og:video:secure_url_video/mp4]").attr("content"),
        false,
        reference.identifier,
        document.selectFirst("meta[property=og:image_video.other]").attr("content")
      );

      return new GetyarnAudioTrack(trackInfo, this);
    } catch (IOException e) {
      throw new FriendlyException("Failed to load info for yarn clip", SUSPICIOUS, null);
    }
  }
}
