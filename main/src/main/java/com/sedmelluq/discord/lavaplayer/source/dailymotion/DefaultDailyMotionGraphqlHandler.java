package com.sedmelluq.discord.lavaplayer.source.dailymotion;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class DefaultDailyMotionGraphqlHandler implements DailyMotionGraphqlHandler {
    private static final String GRAPHQL_URL = "https://graphql.api.dailymotion.com/";
    
    private static final String VIDEO_PAYLOAD = "{\"operationName\": \"WATCHING_VIDEO\",\"query\": \"fragment VIDEO_METADATA on Video { xid title channel { displayName } duration artwork: thumbnailURL(size: \"x1080\") url } query WATCHING_VIDEO($xid: String!) { video: media(xid: $xid) { ... on Video { ...VIDEO_METADATA } } }\",\"variables\": {\"xid\": \"%s\"}}";
    private static final String SIMILAR_PAYLOAD = "{\"operationName\":\"DISCOVERY_QUEUE_QUERY\",\"variables\":{\"videoXid\":\"%s\"},\"query\":\"query DISCOVERY_QUEUE_QUERY($videoXid: String!, $collectionXid: String, $device: String, $videoCountPerSection: Int) { views { neon { sections(device: $device space: \"watching\"  followingChannelXids: [] followingTopicXids: [] watchedVideoXids: [] context: {mediaXid: $videoXid, collectionXid: $collectionXid} first: 25) { edges { node { components(first: $videoCountPerSection) { edges { node { ... on Video { url xid title duration artwork: thumbnailURL(size: \"x1080\") channel { displayName }}}}}}}}}}}\"}";
    private static final String PLAYLIST_PAYLOAD = "{\"operationName\":\"PLAYLIST_VIDEO_QUERY\",\"variables\":{\"xid\":\"%s\"},\"query\":\"fragment COLLECTION_FRAGMENT on Collection { name xid channel { displayName } videos(first: 100) { edges { node { xid duration title channel { displayName } url artwork: thumbnailURL(size: \"x1080\") }}}} query PLAYLIST_VIDEO_QUERY($xid: String!) { collection(xid: $xid) { ...COLLECTION_FRAGMENT }}\"}";
    private static final String CHANNEL_PAYLOAD = "{\"operationName\":\"CHANNEL_QUERY_DESKTOP\",\"variables\":{\"channel_name\":\"%s\"},\"query\":\"fragment VIDEO_FRAGMENT on Video { xid title duration  channel { displayName } url artwork: thumbnailURL(size: \"x1080\")} fragment CHANNEL_MAIN_FRAGMENT on Channel { name displayName avatar: logoURL(size: \"x480\") videos: videos(first: 100) { edges { node { ...VIDEO_FRAGMENT }}}} query CHANNEL_QUERY_DESKTOP($channel_name: String!) { channel(name: $channel_name) { ...CHANNEL_MAIN_FRAGMENT }}\"}";
    private static final String SEARCH_PAYLOAD = "{\"operationName\":\"SEARCH_QUERY\",\"variables\":{\"query\":\"%s\",\"shouldIncludeVideos\":true,\"page\":1,\"limit\":100},\"query\":\"fragment VIDEO_BASE_FRAGMENT on Video { url xid title channel { displayName } duration artwork: thumbnailURL(size: \"x1080\")} query SEARCH_QUERY($query: String!, $shouldIncludeVideos: Boolean!, $page: Int, $limit: Int, $sortByVideos: SearchVideoSort) { search { videos( query: $query first: $limit  page: $page sort: $sortByVideos) @include(if: $shouldIncludeVideos) { edges { node { ...VIDEO_BASE_FRAGMENT }}}}}\"}";

    private final HttpInterfaceManager httpInterfaceManager;

    public DefaultDailyMotionGraphqlHandler(HttpInterfaceManager httpInterfaceManager) {
        this.httpInterfaceManager = httpInterfaceManager;
    }

    @Override
    public AudioTrack video(String id, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            HttpPost post = new HttpPost(GRAPHQL_URL);
            post.setEntity(new StringEntity(String.format(VIDEO_PAYLOAD, id), "UTF-8"));
            try (CloseableHttpResponse response = httpInterface.execute(post)) {
                HttpClientTools.assertSuccessWithContent(response, "video response");

                String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                JsonBrowser json = JsonBrowser.parse(responseText);
                if (json.get("data").isNull()) {
                    throw new Exception(getErrorMessage(json));
                }
                return trackFactory.apply(buildInfo(json.get("data").get("video")));
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to fetch DailyMotion video metadata", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    @Override
    public AudioPlaylist similar(String id, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            HttpPost post = new HttpPost(GRAPHQL_URL);
            post.setEntity(new StringEntity(String.format(SIMILAR_PAYLOAD, id), "UTF-8"));
            try (CloseableHttpResponse response = httpInterface.execute(post)) {
                HttpClientTools.assertSuccessWithContent(response, "video response");

                String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                JsonBrowser json = JsonBrowser.parse(responseText);
                if (json.get("data").isNull()) {
                    throw new Exception(getErrorMessage(json));
                }

                List<AudioTrack> tracks = new ArrayList<>();

                json.get("data").get("views").get("neon").get("sections").get("edges").values()
                .forEach(edge -> edge.get("node").get("components").get("edges").values()
                .forEach(e -> tracks.add(trackFactory.apply(buildInfo(e.get("node"))))));

                return new BasicAudioPlaylist("Similar results for: " + id, null, null, null, "similar", tracks, null, true);
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to fetch DailyMotion similar metadata", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    @Override
    public AudioPlaylist playlist(String id, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            HttpPost post = new HttpPost(GRAPHQL_URL);
            post.setEntity(new StringEntity(String.format(PLAYLIST_PAYLOAD, id), "UTF-8"));
            try (CloseableHttpResponse response = httpInterface.execute(post)) {
                HttpClientTools.assertSuccessWithContent(response, "video response");

                String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                JsonBrowser json = JsonBrowser.parse(responseText);
                if (json.get("data").isNull()) {
                    throw new Exception(getErrorMessage(json));
                }

                List<AudioTrack> tracks = new ArrayList<>();

                JsonBrowser playlist = json.get("data").get("collection");

                playlist.get("videos").get("edges").values()
                .forEach(edge -> tracks.add(trackFactory.apply(buildInfo(edge.get("node")))));

                String name = playlist.get("name").text();
                String creator = playlist.get("channel").get("displayName").text();
                String uri = "https://www.dailymotion.com/playlist/" + playlist.get("xid").text();

                return new BasicAudioPlaylist(name, creator, tracks.get(0).getInfo().artwork, uri, "playlist", tracks, null, false);
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to fetch DailyMotion playlist metadata", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    @Override
    public AudioPlaylist channel(String slug, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            HttpPost post = new HttpPost(GRAPHQL_URL);
            post.setEntity(new StringEntity(String.format(CHANNEL_PAYLOAD, slug), "UTF-8"));
            try (CloseableHttpResponse response = httpInterface.execute(post)) {
                HttpClientTools.assertSuccessWithContent(response, "video response");

                String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                JsonBrowser json = JsonBrowser.parse(responseText);
                if (json.get("data").isNull()) {
                    throw new Exception(getErrorMessage(json));
                }

                List<AudioTrack> tracks = new ArrayList<>();

                JsonBrowser channel = json.get("data").get("channel");

                channel.get("videos").get("edges").values()
                .forEach(edge -> tracks.add(trackFactory.apply(buildInfo(edge.get("node")))));

                String name = channel.get("displayName").text();
                String image = channel.get("avatar").text();
                String url = "https://www.dailymotion.com/" + channel.get("name").text();

                return new BasicAudioPlaylist(name, name, image, url, "channel", tracks, null, false);
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to fetch DailyMotion channel metadata", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    @Override
    public AudioPlaylist search(String query, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            HttpPost post = new HttpPost(GRAPHQL_URL);
            post.setEntity(new StringEntity(String.format(SEARCH_PAYLOAD, query), "UTF-8"));
            try (CloseableHttpResponse response = httpInterface.execute(post)) {
                HttpClientTools.assertSuccessWithContent(response, "video response");

                String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                JsonBrowser json = JsonBrowser.parse(responseText);
                if (json.get("data").isNull()) {
                    throw new Exception(getErrorMessage(json));
                }

                List<AudioTrack> tracks = new ArrayList<>();

                json.get("data").get("search").get("videos").get("edges").values()
                .forEach(edge -> tracks.add(trackFactory.apply(buildInfo(edge.get("node")))));

                return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to fetch DailyMotion search results", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private String getErrorMessage(JsonBrowser root) {
        return root.get("errors").index(0).text();
    }

    private AudioTrackInfo buildInfo(JsonBrowser video) {
        return new AudioTrackInfo(
            video.get("title").text(),
            video.get("channel").get("displayName").text(),
            (long) (video.get("duration").as(Double.class) * 1000.0),
            video.get("xid").text(),
            false,
            video.get("url").text(),
            video.get("artwork").text()
        );
    }
}
