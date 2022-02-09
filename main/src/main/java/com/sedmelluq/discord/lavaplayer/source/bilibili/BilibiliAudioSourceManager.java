package com.sedmelluq.discord.lavaplayer.source.bilibili;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.net.URI;

import static com.sedmelluq.discord.lavaplayer.source.bilibili.BilibiliConstants.VIEW_API;
import static com.sedmelluq.discord.lavaplayer.source.bilibili.BilibiliConstants.SEARCH_API;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class BilibiliAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String SEARCH_PREFIX = "blsearch:";
    private static final String DOMAIN_REGEX = "^(?:http://|https://|)(?:www\\.|m\\.|)bilibili\\.com/(video)[/:]([A-Za-z0-9]+).*";
    private static final Pattern urlPattern = Pattern.compile(DOMAIN_REGEX);

    private final boolean allowSearch;
    private final HttpInterfaceManager httpInterfaceManager;

    public BilibiliAudioSourceManager() {
        this(true);
    }

    public BilibiliAudioSourceManager(boolean allowSearch) {
        this.allowSearch = allowSearch;
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "bilibili";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if (reference.identifier.startsWith(SEARCH_PREFIX) && allowSearch) {
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
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(VIEW_API + "?aid=" + videoId))) {
                HttpClientTools.assertSuccessWithContent(response, "video api response");
                
                JsonBrowser trackMeta = JsonBrowser.parse(response.getEntity().getContent());
                if (Integer.parseInt(trackMeta.get("code").text()) != 0) return null;
                return extractTrackInfo(trackMeta.get("data"));
            }
        } catch (IOException e) {
            throw new FriendlyException("Error occurred when extracting video info.", SUSPICIOUS, e);
        }
    }

    private AudioPlaylist loadSearchResult(String query) {
        try (HttpInterface httpInterface = getHttpInterface()) {
            URI uri = new URIBuilder(SEARCH_API)
            .addParameter("keyword", query)
            .addParameter("search_type", "video").build();
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "search api response");

                JsonBrowser apiResponse = JsonBrowser.parse(response.getEntity().getContent());
                List<JsonBrowser> apiResponseValues = apiResponse.get("data").get("result").values();
                List<AudioTrack> tracks = new ArrayList<>();
                for (JsonBrowser item : apiResponseValues) {
                    String title = item.get("title").text();
                    if (title.contains("<em class=\\\"keyword\\\">")) title.replace("<em class=\\\"keyword\\\">", "");
                    if (title.contains("</em>")) title.replace("</em>", "");
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
        } catch (Exception e) {
            throw new FriendlyException("Error occurred when extracting video info.", SUSPICIOUS, e);
        }
    }

    private AudioItem loadTrack(String videoId) {
        try (HttpInterface httpInterface = getHttpInterface()) {
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(VIEW_API + "?bvid=" + videoId))) {
                HttpClientTools.assertSuccessWithContent(response, "video api response");

                JsonBrowser trackMeta = JsonBrowser.parse(response.getEntity().getContent());
                if (trackMeta.get("code").as(Integer.class) != 0) return null;
                JsonBrowser trackData = trackMeta.get("data");
                if (trackData.get("videos").as(Integer.class) > 1) return extractTracksFromPage(trackData);
                return extractTrackInfo(trackData);
            }
        } catch (IOException e) {
            throw new FriendlyException("Error occurred when extracting video info.", SUSPICIOUS, e);
        }
    }

    private AudioTrack extractTrackInfo(JsonBrowser videoData) {
        String title = videoData.get("title").text();
        String uploader = videoData.get("owner").get("name").text();
        String thumbnailUrl = videoData.get("pic").text();
        long duration = Integer.parseInt(videoData.get("duration").text()) * 1000;
        String videoId = videoData.get("bvid").text();
        String identifier = videoId + "/" + videoData.get("cid").text();
        return new BilibiliAudioTrack(new AudioTrackInfo(title, uploader, duration, identifier, false, getWatchUrl(videoId), thumbnailUrl), this);
    }

    private AudioPlaylist extractTracksFromPage(JsonBrowser videosData) {
        String uploader = videosData.get("owner").get("name").text();
        String thumbnailUrl = videosData.get("pic").text();
        String videoId = videosData.get("bvid").text();

        List<AudioTrack> tracks = new ArrayList<>();
        List<JsonBrowser> videos = videosData.get("pages").values();

        for(int i = 0; i < videos.size(); i++) {
            JsonBrowser video = videos.get(i);
            String title = video.get("part").text();
            long duration = Integer.parseInt(video.get("duration").text()) * 1000;
            String cid = video.get("cid").text();

            AudioTrackInfo trackInfo = new AudioTrackInfo(title, uploader, duration, videoId + "/" + cid, false, getWatchUrl(videoId) + "?p=" + (i + 1), thumbnailUrl);
            tracks.add(new BilibiliAudioTrack(trackInfo, this));
        }

        return new BasicAudioPlaylist(
            "Pages for video: " + videosData.get("title").text(),
            uploader, thumbnailUrl, getWatchUrl(videoId), "pages",
            tracks, null, false
        );
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