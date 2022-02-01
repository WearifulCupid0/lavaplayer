package com.sedmelluq.discord.lavaplayer.source.bilibili;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import java.util.ArrayList;
import java.util.List;
import java.net.URLEncoder;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
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
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BilibiliAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String SEARCH_PREFIX = "blsearch:";
    private static final String DOMAIN_REGEX = "^(?:http://|https://|)(?:www\\.|m\\.|)bilibili\\.com/(video)[/:]([A-Za-z0-9]+).*";
    private final HttpInterfaceManager httpInterfaceManager;
    private static final Pattern urlPattern = Pattern.compile(DOMAIN_REGEX);
    private static final Logger log = LoggerFactory.getLogger(BilibiliAudioSourceManager.class);

    public BilibiliAudioSourceManager() {
    httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "bilibili";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        log.debug("received to load {}", reference.identifier);
        if(reference.identifier.startsWith(SEARCH_PREFIX)) {
            return loadSearchResult(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
        }
        Matcher urlMatcher = urlPattern.matcher(reference.identifier);
        boolean matchFound = urlMatcher.find();
        if (matchFound) {
            String videoId = urlMatcher.group(2);
            if (videoId.startsWith("av")) return loadTrackFromAid(videoId.split("av")[1]);
            return loadTrack(videoId); 
        }
        return null;
    }

    private AudioTrack loadTrackFromAid(String videoId) {
        try (HttpInterface httpInterface = getHttpInterface()) {
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://api.bilibili.com/x/web-interface/view?aid=" + videoId))) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                    throw new IOException("Unexpected response code from video info: " + statusCode);
                }
            JsonBrowser trackMeta = JsonBrowser.parse(response.getEntity().getContent());
            if (Integer.parseInt(trackMeta.get("code").text()) != 0) return null;
            return extractTrackInfo(trackMeta);
        }
    } catch (IOException e) {
        throw new FriendlyException("Error occurred when extracting video info.", SUSPICIOUS, e);
        }
    }

    private AudioPlaylist loadSearchResult(String query) {
        try (HttpInterface httpInterface = getHttpInterface()) {
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://api.bilibili.com/x/web-interface/search/type?keyword=" + URLEncoder.encode(query, "UTF-8") + "&search_type=video"))) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                    throw new IOException("Unexpected response code from video info: " + statusCode);
                }
            JsonBrowser apiResponse = JsonBrowser.parse(response.getEntity().getContent());
            List<JsonBrowser> apiResponseValues = apiResponse.get("data").get("result").values();
            List<AudioTrack> tracks = new ArrayList<>();
            for (JsonBrowser item : apiResponseValues) {
                String title = item.get("title").text();
                String uploader = item.get("author").text();
                String thumbnailUrl = "http:" + item.get("pic").text();
                long duration = DataFormatTools.durationTextToMillis(item.get("duration").text());
                String videoId = item.get("bvid").text();
                tracks.add(
                    new BilibiliAudioTrack(new AudioTrackInfo(title, uploader, duration, videoId, false, getWatchUrl(videoId), thumbnailUrl), this)
                );
            }
            return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
        }
    } catch (IOException e) {
        throw new FriendlyException("Error occurred when extracting video info.", SUSPICIOUS, e);
        }
    }

    private AudioTrack loadTrack(String videoId) {
        try (HttpInterface httpInterface = getHttpInterface()) {
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://api.bilibili.com/x/web-interface/view?bvid=" + videoId))) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                    throw new IOException("Unexpected response code from video info: " + statusCode);
                }
            JsonBrowser trackMeta = JsonBrowser.parse(response.getEntity().getContent());
            if (Integer.parseInt(trackMeta.get("code").text()) != 0) return null;
            return extractTrackInfo(trackMeta);
        }
    } catch (IOException e) {
        throw new FriendlyException("Error occurred when extracting video info.", SUSPICIOUS, e);
        }
    }

    private AudioTrack extractTrackInfo(JsonBrowser videoMeta) {
        JsonBrowser trackMeta = videoMeta.get("data");
        String title = trackMeta.get("title").text();
        String uploader = trackMeta.get("owner").get("name").text();
        String thumbnailUrl = trackMeta.get("pic").text();
        long duration = Integer.parseInt(trackMeta.get("duration").text()) * 1000;
        String videoId = trackMeta.get("bvid").text();
        return new BilibiliAudioTrack(new AudioTrackInfo(title,
        uploader,
        duration,
        videoId,
        false,
        getWatchUrl(videoId),
        thumbnailUrl), this);
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
        return new BilibiliAudioTrack(trackInfo, this);
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

    private static String getWatchUrl(String videoId) {
        return "https://www.bilibili.com/video/" + videoId;
    }
}