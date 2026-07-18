package com.sedmelluq.lavaplayer.source.nico;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio source manager that implements finding NicoNico tracks based on URL.
 */
public class NicoAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String TRACK_URL_REGEX = "^(?:http://|https://|)(?:www\\.|)nicovideo\\.jp/watch/(.{2}[0-9]+)(?:\\?.*|)$";

    private static final String SEARCH_API_URL = "https://www.nicovideo.jp/search/%s?responseType=json";

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0";

    public static final String SEARCH_PREFIX = "nvsearch:";
    private static final Pattern trackUrlPattern = Pattern.compile(TRACK_URL_REGEX);

    private final HttpInterfaceManager httpInterfaceManager;
    private final AtomicBoolean loggedIn;
    private final Boolean allowSearch;

    public NicoAudioSourceManager() {
        this(true);
    }

    public NicoAudioSourceManager(boolean allowSearch) {
        this(null, null, allowSearch);
    }

    /**
     * @param email    Site account email
     * @param password Site account password
     */
    public NicoAudioSourceManager(String email, String password, boolean allowSearch) {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
        loggedIn = new AtomicBoolean();
        // Log in at the start
        if (!DataFormatTools.isNullOrEmpty(email) && !DataFormatTools.isNullOrEmpty(password)) {
            logIn(email,password);
        }

        this.allowSearch = allowSearch;
    }

    @Override
    public String getSourceName() {
        return "niconico";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        Matcher trackMatcher = trackUrlPattern.matcher(reference.identifier);

        if (trackMatcher.matches()) {
            return loadTrack(trackMatcher.group(1));
        }

        if (reference.identifier.startsWith(SEARCH_PREFIX) && allowSearch) {
            return loadSearch(reference.identifier.substring(SEARCH_PREFIX.length()));
        }

        return null;
    }

    private AudioItem loadSearch(String query) {
        try (HttpInterface httpInterface = getHttpInterface()) {
            HttpGet get = new HttpGet(buildSearchURI(query));
            get.addHeader("user-agent", USER_AGENT);
            try (CloseableHttpResponse response = httpInterface.execute(get)) {
                if (response.getStatusLine().getStatusCode() == 404)
                    return AudioReference.NO_TRACK;

                HttpClientTools.assertSuccessWithContent(response, "search api response");

                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

                List<AudioTrack> tracks = new ArrayList<>();

                for (JsonBrowser item : json.get("data").get("response").get("$getSearchVideoV2").get("data").get("items").values()) {
                    if (!json.isNull())
                        tracks.add(extractTrack(item));
                }

                return BasicAudioPlaylist.createSearchResults(query, tracks);
            }
        } catch (IOException e) {
            throw new FriendlyException("Error occurred when loading search results.", SUSPICIOUS, e);
        }
    }

    private AudioTrack extractTrack(JsonBrowser json) {
        String id = json.get("id").text();
        AudioTrackInfo trackInfo = new AudioTrackInfo(
                json.get("title").text(),
                new AudioTrackAuthorInfo(json.get("owner").get("name").text(), getUserUrl(json.get("owner").get("id").text())),
                json.get("duration").asLong(0) * 1000,
                id,
                false,
                getWatchUrl(id),
                json.get("thumbnail").get("nHdUrl").text()
        );

        return new NicoAudioTrack(trackInfo, this);
    }

    private AudioTrack loadTrack(String videoId) {
        try (HttpInterface httpInterface = getHttpInterface()) {
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://ext.nicovideo.jp/api/getthumbinfo/" + videoId))) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                    throw new IOException("Unexpected response code from video info: " + statusCode);
                }

                Document document = Jsoup.parse(response.getEntity().getContent(), StandardCharsets.UTF_8.name(), "", Parser.xmlParser());
                return extractTrackFromXml(videoId, document);
            }
        } catch (IOException e) {
            throw new FriendlyException("Error occurred when extracting video info.", SUSPICIOUS, e);
        }
    }

    private AudioTrack extractTrackFromXml(String videoId, Document document) {
        for (Element element : document.select(":root > thumb")) {
            String uploader = element.selectFirst("user_nickname").text();
            String uploaderId = element.selectFirst("user_id").text();
            String title = element.selectFirst("title").text();
            String thumbnailUrl = element.selectFirst("thumbnail_url").text();
            long duration = DataFormatTools.durationTextToMillis(element.selectFirst("length").text());

            return new NicoAudioTrack(new AudioTrackInfo(title,
                new AudioTrackAuthorInfo(uploader, getUserUrl(uploaderId)),
                duration,
                videoId,
                false,
                getWatchUrl(videoId),
                thumbnailUrl,
                null
            ), this);
        }

        return null;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        // No extra information to save
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new NicoAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        // Nothing to shut down
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

    void logIn(String email, String password) {
        synchronized (loggedIn) {
            if (loggedIn.get()) {
                return;
            }

            HttpPost loginRequest = new HttpPost("https://secure.nicovideo.jp/secure/login");

            loginRequest.setEntity(new UrlEncodedFormEntity(Arrays.asList(
                new BasicNameValuePair("mail", email),
                new BasicNameValuePair("password", password)
            ), StandardCharsets.UTF_8));

            try (HttpInterface httpInterface = getHttpInterface()) {
                try (CloseableHttpResponse response = httpInterface.execute(loginRequest)) {
                    int statusCode = response.getStatusLine().getStatusCode();

                    if (statusCode != 302) {
                        throw new IOException("Unexpected response code " + statusCode);
                    }

                    Header location = response.getFirstHeader("Location");

                    if (location == null || location.getValue().contains("message=")) {
                        throw new FriendlyException("Login details for NicoNico are invalid.", COMMON, null);
                    }

                    loggedIn.set(true);
                }
            } catch (IOException e) {
                throw new FriendlyException("Exception when trying to log into NicoNico", SUSPICIOUS, e);
            }
        }
    }

    private static String getWatchUrl(String videoId) {
        return "https://www.nicovideo.jp/watch/" + videoId;
    }

    private static String getUserUrl(String userId) { return "https://www.nicovideo.jp/user/" + userId; }

    private static String buildSearchURI(String query) {
        return String.format(SEARCH_API_URL, URLEncoder.encode(query, StandardCharsets.UTF_8));
    }
}
