package com.sedmelluq.discord.lavaplayer.source.youtube;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
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

import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.BROWSE_CHANNEL_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.WATCH_URL_PREFIX;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.BROWSE_URL;

public class DefaultYoutubeChannelLoader implements YoutubeChannelLoader {
    @Override
    public AudioItem load(HttpInterface httpInterface, String channelId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        HttpPost post = new HttpPost(BROWSE_URL);
        StringEntity payload = new StringEntity(String.format(BROWSE_CHANNEL_PAYLOAD, channelId), "UTF-8");
        post.setEntity(payload);
        try (CloseableHttpResponse response = httpInterface.execute(post)) {
            HttpClientTools.assertSuccessWithContent(response, "channel response");
            HttpClientTools.assertJsonContentType(response);

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            return buildChannel(json, trackFactory);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
    private AudioItem buildChannel(JsonBrowser json, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        JsonBrowser channelData = json.get("metadata").get("channelMetadataRenderer");
        if (channelData == null) channelData = json.get("header").get("c4TabbedHeaderRenderer");

        String channelName = channelData.get("title").text();

        List<AudioTrack> tracks = new ArrayList<>();
        List<JsonBrowser> videos = json
        .get("contents")
        .get("twoColumnBrowseResultsRenderer")
        .get("tabs")
        .index(1)
        .get("tabRenderer")
        .get("content")
        .get("sectionListRenderer")
        .get("contents")
        .index(0)
        .get("itemSectionRenderer")
        .get("contents")
        .index(0)
        .get("gridRenderer")
        .get("items")
        .values();

        videos.forEach(video -> {
            AudioTrack track = extractTrack(video, channelName, trackFactory);
            if(track != null) tracks.add(track);
        });

        if(tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new BasicAudioPlaylist(channelName, "channel", tracks, null, false);
    }
    private AudioTrack extractTrack(JsonBrowser json, String channelName, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        JsonBrowser renderer = json.get("gridVideoRenderer");
        if(renderer.isNull()) {
            return null;
        }

        String title = renderer.get("title").get("runs").index(0).get("text").text();
        String videoId = renderer.get("videoId").text();
        String uri = WATCH_URL_PREFIX + videoId;
        List<JsonBrowser> artworks = renderer.get("thumbnail").get("thumbnails").values();
        String artwork = artworks.get(artworks.size() - 1).get("url").text();
        long duration = DataFormatTools.durationTextToMillis(renderer.get("thumbnailOverlays").index(0).get("thumbnailOverlayTimeStatusRenderer").get("text").get("simpleText").text());

        return trackFactory.apply(new AudioTrackInfo(title, channelName, duration, videoId, false, uri, artwork));
    }
}
