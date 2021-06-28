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

import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.NEXT_VIDEO_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.WATCH_URL_PREFIX;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.NEXT_URL;
import static com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN;

public class DefaultYoutubeSimilarLoader implements YoutubeSimilarLoader {
    @Override
    public AudioItem load(HttpInterface httpInterface, String videoId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        HttpPost post = new HttpPost(NEXT_URL);
        StringEntity payload = new StringEntity(String.format(NEXT_VIDEO_PAYLOAD, videoId), "UTF-8");
        post.setEntity(payload);
        try (CloseableHttpResponse response = httpInterface.execute(post)) {
            HttpClientTools.assertSuccessWithContent(response, "channel response");
            HttpClientTools.assertJsonContentType(response);

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            return buildResults(json, trackFactory);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
    private AudioItem buildResults(JsonBrowser json, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        List<AudioTrack> tracks = new ArrayList<>();
        List<JsonBrowser> videos = json
        .get("contents")
        .get("twoColumnWatchNextResults")
        .get("secondaryResults")
        .get("secondaryResults")
        .get("results")
        .index(1)
        .get("itemSectionRenderer")
        .get("contents")
        .values();

        videos.forEach(video -> {
            AudioTrack track = extractTrack(video, trackFactory);
            if (track != null) tracks.add(track);
        });

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        String title = json
        .get("contents")
        .get("twoColumnWatchNextResults")
        .get("results")
        .get("results")
        .get("contents")
        .index(0)
        .get("videoPrimaryInfoRenderer")
        .get("title")
        .get("runs")
        .index(0)
        .get("text")
        .text();

        return new BasicAudioPlaylist("Similar videos for: " + title, "similar", tracks, null, false);
    }
    private AudioTrack extractTrack(JsonBrowser json, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        json = json.get("compactVideoRenderer");
        if(json.isNull()) return null; // Ignore everything which is not a track

        AudioTrackInfo info = null;
        String videoId = json.get("videoId").text();
        String title = json.get("title").get("simpleText").text();
        String author = json.get("shortBylineText").get("runs").index(0).get("text").text();
        List<JsonBrowser> artworks = json.get("thumbnail").get("thumbnails").values();
        String artwork = artworks.get(artworks.size() - 1).get("url").text();
        if (json.get("lengthText").isNull()) {
            info = new AudioTrackInfo(title, author, DURATION_MS_UNKNOWN, videoId, true,
            WATCH_URL_PREFIX + videoId, artwork);

        } else {
        long duration = DataFormatTools.durationTextToMillis(json.get("lengthText").get("simpleText").text());
          
        info = new AudioTrackInfo(title, author, duration, videoId, false,
            WATCH_URL_PREFIX + videoId, artwork);
        }
        return trackFactory.apply(info);
    }
}
