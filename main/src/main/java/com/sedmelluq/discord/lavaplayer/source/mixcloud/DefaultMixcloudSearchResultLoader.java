package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.function.Function;

import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.MIXCLOUD_SEARCH_API;
import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudUtils.buildIdentifier;
import static java.nio.charset.StandardCharsets.UTF_8;

public class DefaultMixcloudSearchResultLoader implements MixcloudSearchProvider {
    private final HttpInterfaceManager httpInterfaceManager;

    public DefaultMixcloudSearchResultLoader() {
        this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
    }

    public ExtendedHttpConfigurable getHttpConfiguration() {
        return httpInterfaceManager;
    }

    public AudioItem loadSearchResults(String query, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(MIXCLOUD_SEARCH_API + URLEncoder.encode(query, "UTF-8")))) {
                HttpClientTools.assertSuccessWithContent(response, "search response");

                String responseText = EntityUtils.toString(response.getEntity(), UTF_8);
                JsonBrowser jsonBrowser = JsonBrowser.parse(responseText);

                return extractTracks(jsonBrowser.get("data"), query, trackFactory);
            }
        } catch (Exception e) {
            throw ExceptionTools.wrapUnfriendlyExceptions(e);
        }
    }

    private AudioItem extractTracks(JsonBrowser data, String query, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        if(data.isNull()) {
            return AudioReference.NO_TRACK;
        }

        ArrayList<AudioTrack> tracks = new ArrayList<>();
        data.values()
        .forEach(t -> {
            AudioTrack track = extractTrack(t, trackFactory);
            if(track != null) {
                tracks.add(track);
            }
        });

        if(tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new BasicAudioPlaylist("Search results for: " + query, "search", tracks, null, true);
    }

    private AudioTrack extractTrack(JsonBrowser track, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        String title = track.get("name").text();
        String author = track.get("user").get("name").text();
        String uri = track.get("url").text();
        String artwork = track.get("pictures").get("1024wx1024h").text();
        String identifier = buildIdentifier(track.get("slug").text(), track.get("user").get("username").text());

        return trackFactory.apply(new AudioTrackInfo(title, author, (long) (track.get("audio_length").as(Double.class) * 1000.0), identifier, false, uri, artwork));
    }
}
