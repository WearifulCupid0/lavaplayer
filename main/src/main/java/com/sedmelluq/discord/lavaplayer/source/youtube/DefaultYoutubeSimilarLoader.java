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

import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.NEXT_VIDEO_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.WATCH_URL_PREFIX;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.NEXT_URL;
import static com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN;

public class DefaultYoutubeSimilarLoader implements YoutubeSimilarLoader {
    @Override
    public AudioPlaylist load(String videoId, HttpInterface httpInterface, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        HttpPost post = new HttpPost(NEXT_URL);
        StringEntity payload = new StringEntity(String.format(NEXT_VIDEO_PAYLOAD, videoId), "UTF-8");
        post.setEntity(payload);
        try (CloseableHttpResponse response = httpInterface.execute(post)) {
            HttpClientTools.assertSuccessWithContent(response, "channel response");
            HttpClientTools.assertJsonContentType(response);

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            return buildResults(json, trackFactory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private AudioPlaylist buildResults(JsonBrowser json, Function<AudioTrackInfo, AudioTrack> trackFactory) throws IOException {
        List<AudioTrack> tracks = new ArrayList<>();
        List<JsonBrowser> data = json
        .get("contents")
        .get("singleColumnWatchNextResults")
        .get("results")
        .get("results")
        .get("contents")
        .values();

        JsonBrowser videos = data
        .get(1)
        .get("shelfRenderer")
        .get("content")
        .get("horizontalListRenderer")
        .get("items");

        videos.values().forEach(video -> {
            AudioTrack track = extractTrack(video, trackFactory);
            if (track != null) tracks.add(track);
        });

        String title = data
        .get(0)
        .get("slimVideoMetadataSectionRenderer")
        .get("contents")
        .index(0)
        .get("slimVideoInformationRenderer")
        .get("title")
        .get("runs")
        .index(0)
        .get("text")
        .text();

        if (tracks.isEmpty()) {
            return null;
        }

        return new BasicAudioPlaylist("Similar videos for: " + title, null, null, null, "similar", tracks, null, true);
    }
    private AudioTrack extractTrack(JsonBrowser json, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        json = json.get("gridVideoRenderer");
        if (json.isNull()) {
            return null; // Ignore everything which is not a track
        }

        AudioTrackInfo info = null;
        String videoId = json.get("videoId").text();
        String title = json.get("title").get("runs").index(0).get("text").text();
        String author = json.get("shortBylineText").get("runs").index(0).get("text").text();
        String artwork = PBJUtils.getYouTubeThumbnail(json, videoId);
        if (json.get("lengthText").isNull() || json.get("lengthText").get("runs").isNull()) {
            info = new AudioTrackInfo(title, author, DURATION_MS_UNKNOWN, videoId, true,
            WATCH_URL_PREFIX + videoId, artwork);

        } else {
        long duration = DataFormatTools.durationTextToMillis(json.get("lengthText").get("runs").index(0).get("text").text());
          
        info = new AudioTrackInfo(title, author, duration, videoId, false,
            WATCH_URL_PREFIX + videoId, artwork);
        }
        return trackFactory.apply(info);
    }
}
