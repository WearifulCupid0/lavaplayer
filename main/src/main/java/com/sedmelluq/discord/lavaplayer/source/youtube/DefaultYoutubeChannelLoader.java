package com.sedmelluq.discord.lavaplayer.source.youtube;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.PBJUtils;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.BROWSE_CONTINUATION_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.BROWSE_CHANNEL_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.WATCH_URL_PREFIX;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.BROWSE_URL;

public class DefaultYoutubeChannelLoader implements YoutubeChannelLoader {
    @Override
    public AudioPlaylist load(String channelId, HttpInterface httpInterface, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        HttpPost post = new HttpPost(BROWSE_URL);
        StringEntity payload = new StringEntity(String.format(BROWSE_CHANNEL_PAYLOAD, channelId), "UTF-8");
        post.setEntity(payload);
        try (CloseableHttpResponse response = httpInterface.execute(post)) {
            HttpClientTools.assertSuccessWithContent(response, "channel response");
            HttpClientTools.assertJsonContentType(response);

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            return buildChannel(json, httpInterface, trackFactory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private AudioPlaylist buildChannel(JsonBrowser json, HttpInterface httpInterface, Function<AudioTrackInfo, AudioTrack> trackFactory) throws IOException {
        JsonBrowser header = json.get("header").get("c4TabbedHeaderRenderer");
        String channelName = header.get("title").text();
        List<JsonBrowser> avatars = header.get("avatar").get("thumbnails").values();
        String avatar = avatars.get(avatars.size() - 1).get("url").text();
        String continuationToken = null;
        List<AudioTrack> tracks = new ArrayList<>();
        JsonBrowser sectionRenderer = json
        .get("contents")
        .get("singleColumnBrowseResultsRenderer")
        .get("tabs")
        .index(1)
        .get("tabRenderer")
        .get("content")
        .get("sectionListRenderer")
        .get("contents")
        .index(0)
        .get("itemSectionRenderer");

        sectionRenderer.get("contents").values().forEach(video -> {
            AudioTrack track = extractTrack(video, channelName, trackFactory);
            if(track != null) tracks.add(track);
        });

        JsonBrowser continuationData = sectionRenderer.get("continuations")
        .index(0)
        .get("nextContinuationData");

        if(!continuationData.isNull()) {
            continuationToken = continuationData.get("continuation").text();
        }

        while(continuationToken != null) {
            HttpPost post = new HttpPost(BROWSE_URL);
            StringEntity payload = new StringEntity(String.format(BROWSE_CONTINUATION_PAYLOAD, continuationToken), "UTF-8");
            post.setEntity(payload);
            try (CloseableHttpResponse response = httpInterface.execute(post)) {
                HttpClientTools.assertSuccessWithContent(response, "channel response");

                JsonBrowser continuationJson = JsonBrowser.parse(response.getEntity().getContent());

                continuationJson
                .get("continuationContents")
                .get("itemSectionContinuation")
                .get("contents")
                .values()
                .forEach(video -> {
                    AudioTrack track = extractTrack(video, channelName, trackFactory);
                    if(track != null) tracks.add(track);
                });

                continuationData = continuationJson
                .get("continuationContents")
                .get("itemSectionContinuation")
                .get("continuations")
                .index(0)
                .get("nextContinuationData");
                
                if(!continuationData.isNull()) {
                    continuationToken = continuationData.get("continuation").text();
                } else {
                    continuationToken = null;
                }
            }
        }

        if (tracks.isEmpty()) {
            return null;
        }

        return new BasicAudioPlaylist(
            channelName,
            channelName,
            avatar,
            "https://www.youtube.com/channel/" + header.get("channelId").text(),
            "channel",
            tracks,
            null,
            false
        );
    }
    private AudioTrack extractTrack(JsonBrowser json, String channelName, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        JsonBrowser renderer = json.get("elementRenderer");
        if(renderer.isNull()) {
            return null;
        }
        JsonBrowser videoModel = renderer
        .get("newElement")
        .get("type")
        .get("componentType")
        .get("model")
        .get("compactVideoModel")
        .get("compactVideoData");

        String title = videoModel.get("videoData").get("metadata").get("title").text();
        String videoId = videoModel.get("onTap").get("innertubeCommand").get("watchEndpoint").get("videoId").text();
        String uri = WATCH_URL_PREFIX + videoId;
        long duration = DataFormatTools.durationTextToMillis(videoModel.get("videoData").get("thumbnail").get("timestampText").text());

        return trackFactory.apply(new AudioTrackInfo(title, channelName, duration, videoId, false, uri, PBJUtils.getYouTubeThumbnail(videoId)));
    }
}
