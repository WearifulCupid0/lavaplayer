package com.sedmelluq.discord.lavaplayer.source.google.podcasts;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GooglePodcastsAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String EPISODE_REGEX = "^(?:https://|http://|)podcasts\\.google\\.com/feed/([a-zA-Z0-9-_]+)/episode/([a-zA-Z0-9-_]+)";
    private static final String PODCAST_REGEX = "^(?:https://|http://|)podcasts\\.google\\.com/feed/([a-zA-Z0-9-_]+)";

    private static final Pattern episodePattern = Pattern.compile(EPISODE_REGEX);
    private static final Pattern podcastPattern = Pattern.compile(PODCAST_REGEX);
    private static final String SEARCH_PREFIX = "gpsearch:";
    private final boolean allowSearch;
    private final HttpInterfaceManager httpInterfaceManager;
    private final MediaContainerRegistry mediaContainerRegistry;

    public GooglePodcastsAudioSourceManager() { this(MediaContainerRegistry.DEFAULT_REGISTRY); }

    public GooglePodcastsAudioSourceManager(MediaContainerRegistry mediaContainerRegistry) { this(true, mediaContainerRegistry); }

    public GooglePodcastsAudioSourceManager(boolean allowSearch, MediaContainerRegistry mediaContainerRegistry) {
        this.allowSearch = allowSearch;
        this.mediaContainerRegistry = mediaContainerRegistry;

        httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "google-podcasts";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if (reference.identifier.startsWith(SEARCH_PREFIX) && this.allowSearch) {
            return this.getSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
        }
        Matcher m;

        if ((m = episodePattern.matcher(reference.identifier)).matches()) {
            return this.getEpisode(m.group(1), m.group(2));
        }

        if ((m = podcastPattern.matcher(reference.identifier)).matches()) {
            return this.getPodcast(m.group(1));
        }

        return null;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {

    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new GooglePodcastsAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
    }

    public MediaContainerRegistry getMediaContainerRegistry() { return mediaContainerRegistry; }

    public HttpInterface getInterface() { return httpInterfaceManager.getInterface(); }

    private AudioItem getEpisode(String feedId, String episodeId) {
        JsonBrowser json = this.callAPI(String.format(GooglePodcastsConstants.EPISODE_PAYLOAD, feedId, episodeId));
        if (json == null) return AudioReference.NO_TRACK;
        if (json.index(1).isNull()) return AudioReference.NO_TRACK;
        return buildTrack(json.index(1));
    }

    private AudioItem getPodcast(String feedId) {
        JsonBrowser json = this.callAPI(String.format(GooglePodcastsConstants.PODCAST_PAYLOAD, feedId));
        if (json == null) return AudioReference.NO_TRACK;
        List<JsonBrowser> jsons = json.index(1).index(0).values();
        if (jsons.size() <= 0) return AudioReference.NO_TRACK;
        List<AudioTrack> tracks = new ArrayList<>();
        jsons.forEach((j) -> tracks.add(buildTrack(j)));
        JsonBrowser info = json.index(3);
        return new BasicAudioPlaylist(info.index(0).text(), info.index(1).text(), info.index(16).index(0).text(), String.format(GooglePodcastsConstants.PODCAST_URL, info.index(10).text()), "podcast", tracks, null, false);
    }

    private AudioItem getSearch(String query) {
        JsonBrowser json = this.callAPI(String.format(GooglePodcastsConstants.SEARCH_PAYLOAD, query));
        if (json == null) return AudioReference.NO_TRACK;
        List<JsonBrowser> jsons = json.index(1).index(1).index(1).index(0).index(1).index(0).values();
        if (jsons.size() <= 0) return AudioReference.NO_TRACK;
        List<AudioTrack> tracks = new ArrayList<>();
        jsons.forEach((j) -> tracks.add(buildTrack(j)));
        return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
    }

    private AudioTrack buildTrack(JsonBrowser trackData) {
        List<JsonBrowser> list = trackData.index(4).values();
        String feedId = list.get(list.size() - 2).text();
        String epId = list.get(list.size() - 1).text();
        AudioTrackInfo trackInfo = new AudioTrackInfo(
            trackData.index(8).safeText(),
            trackData.index(1).safeText(),
            (long) (trackData.index(12).as(Double.class) * 1000.0),
            trackData.index(13).safeText(),
            false,
            String.format(GooglePodcastsConstants.EPISODE_URL, feedId, epId),
            trackData.index(15).index(0).text(),
            trackData.index(5).asBoolean(false)
        );
        return new GooglePodcastsAudioTrack(trackInfo, this);
    }

    private JsonBrowser callAPI(String payload) {
        HttpPost post = new HttpPost(GooglePodcastsConstants.API_URL);
        String encoded = URLEncoder.encode(payload, StandardCharsets.UTF_8);
        post.setEntity(new StringEntity("f.req=" + encoded, ContentType.APPLICATION_FORM_URLENCODED));
        post.setHeader("content-type", "application/x-www-form-urlencoded");
        try (CloseableHttpResponse response = getInterface().execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("Invalid status code: " + Integer.toString(statusCode));
            }

            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser resp = JsonBrowser.parse(responseText.split("\n")[2]);
            JsonBrowser json = resp.index(0).index(2);
            if (json.isNull()) return null;
            return JsonBrowser.parse(json.text());
        } catch (IOException e) {
            throw new FriendlyException("Failed to make a request to Google Podcasts api", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }
}
