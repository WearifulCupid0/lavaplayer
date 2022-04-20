package com.sedmelluq.discord.lavaplayer.source.youtube;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.PBJUtils;
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

import static com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.NEXT_URL;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.NEXT_SIMILAR_PAYLOAD;
import static com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants.WATCH_URL_PREFIX;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Handles loading of YouTube similar tracks.
 */
public class YoutubeSimilarTrackProvider implements YoutubeSimilarTrackLoader {
    /**
     * Loads tracks from a main track.
     *
     * @param videoId ID of the main video
     * @return Playlist of the similar tracks.
     */
    public AudioItem load(HttpInterface httpInterface, String videoId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        List<AudioTrack> tracks = new ArrayList<>();

        HttpPost post = new HttpPost(NEXT_URL);
        StringEntity payload = new StringEntity(String.format(NEXT_SIMILAR_PAYLOAD, videoId), "UTF-8");
        post.setEntity(payload);

        try (CloseableHttpResponse response = httpInterface.execute(post)) {
            HttpClientTools.assertSuccessWithContent(response, "track similar response");

            JsonBrowser body = JsonBrowser.parse(response.getEntity().getContent());
            List<JsonBrowser> contents = body.get("contents")
                    .get("singleColumnWatchNextResults")
                    .get("results")
                    .get("results")
                    .get("contents").values();
            
            JsonBrowser shelfRenderer = null;

            for (JsonBrowser content : contents) {
                if (!content.get("shelfRenderer").isNull()) {
                    shelfRenderer = content.get("shelfRenderer");
                    break;
                }
            }
            
            if (shelfRenderer == null || shelfRenderer.isNull()) return AudioReference.NO_TRACK;
            List<JsonBrowser> values = shelfRenderer
            .get("content")
            .get("horizontalListRenderer")
            .get("items").values();

            if (values.isEmpty()) return AudioReference.NO_TRACK;

            values.forEach(json -> {
                AudioTrack track = extractTrack(json, trackFactory);
                if (track != null) tracks.add(track);
            });

            if (tracks.isEmpty()) return AudioReference.NO_TRACK;
        } catch (IOException e) {
            throw new FriendlyException("Could not read track page.", SUSPICIOUS, e);
        }

        return new BasicAudioPlaylist("Similar tracks results from track: " + videoId, null, null, null, "similar", tracks, null, true);
    }

    private AudioTrack extractTrack(
        JsonBrowser video,
        Function<AudioTrackInfo, AudioTrack> trackFactory
    ) {
        JsonBrowser renderer = video.get("gridVideoRenderer");

        if (renderer.isNull()) return null;

        String title = renderer.get("title").get("runs").index(0).get("text").text();
        JsonBrowser authorData = renderer.get("longBylineText").isNull() ? renderer.get("shortBylineText") : renderer.get("longBylineText");
        String author = authorData.get("runs").index(0).get("text").text();
        String durationStr = renderer.get("lengthText").get("runs").index(0).get("text").text();
        long duration = durationStr != null ? DataFormatTools.durationTextToMillis(durationStr) : DURATION_MS_UNKNOWN;
        String identifier = renderer.get("videoId").text();
        String uri = WATCH_URL_PREFIX + identifier;

        AudioTrackInfo trackInfo = new AudioTrackInfo(title, author, duration, identifier, duration == DURATION_MS_UNKNOWN, uri, PBJUtils.getYouTubeThumbnail(renderer, identifier));
        return trackFactory.apply(trackInfo);
    }
}
