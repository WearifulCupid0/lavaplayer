package com.sedmelluq.discord.lavaplayer.source.saavn;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.config.RequestConfig;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SaavnAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private final static String REGEX = "^(?:http://|https://|)(?:www\\.|)jiosaavn\\.com/(song|album|featured)/(?:.*)/([a-zA-Z0-9-_]+)$";
    private final static Pattern regexPattern = Pattern.compile(REGEX);

    private final String SEARCH_PREFIX = "sasearch:";

    private final Boolean allowSearch;
    public final SaavnApiRequester apiRequester;
    private final HttpInterfaceManager httpInterfaceManager;

    /**
     * Create an instance.
     */
    public SaavnAudioSourceManager() {
        this(true);
    }

    public SaavnAudioSourceManager(Boolean allowSearch) {
        this.allowSearch = allowSearch;
        this.apiRequester = new SaavnApiRequester(this);

        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "jiosaavn";
    }

    @Override
    public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
        Matcher matcher = regexPattern.matcher(reference.identifier);
        if(matcher.find()) {
            String type = matcher.group(1);
            String id = matcher.group(2);
            if(type == "song") {
                return buildTrack(apiRequester.track(id), id);
            }
            if(type == "album") {
                List<AudioTrack> tracks = new ArrayList<>();
                JsonBrowser album = apiRequester.album(id);
                album.get("list").values()
                .forEach(song -> {
                    String identifier = regexPattern.matcher(song.get("perma_url").text()).group(2);
                    tracks.add(buildTrack(song, identifier));
                });
                return new BasicAudioPlaylist(
                    album.get("title").text(),
                    album.get("more_info").get("artistMap")
                    .get("primary_artists").values().stream()
                    .map(e -> e.get("name").text())
                    .collect(Collectors.joining(", ")),
                    album.get("image").text(),
                    album.get("perma_url").text(),
                    "album",
                    tracks,
                    null,
                    false
                );
            }
            if(type == "featured") {
                List<AudioTrack> tracks = new ArrayList<>();
                JsonBrowser playlist = apiRequester.playlist(id);
                playlist.get("list").values()
                .forEach(song -> {
                    String identifier = regexPattern.matcher(song.get("perma_url").text()).group(2);
                    tracks.add(buildTrack(song, identifier));
                });
                String creator = playlist.get("more_info").get("firstname").text();
                if(
                    !playlist.get("more_info").get("lastname").isNull() &&
                    playlist.get("more_info").get("lastname").text().length() > 0
                ) creator = creator + " " + playlist.get("more_info").get("lastname").text();
                return new BasicAudioPlaylist(
                    playlist.get("title").text(),
                    creator, 
                    playlist.get("image").text(),
                    playlist.get("perma_url").text(),
                    "playlist",
                    tracks,
                    null,
                    false
                );
            }
        }
        if(allowSearch && reference.identifier.startsWith(SEARCH_PREFIX)) {
            List<AudioTrack> tracks = new ArrayList<>();
            String query = reference.identifier.substring(SEARCH_PREFIX.length()).trim();
            List<JsonBrowser> results = apiRequester.search(query);
            results.forEach(song -> {
                String identifier = regexPattern.matcher(song.get("perma_url").text()).group(2);
                tracks.add(buildTrack(song, identifier));
            });
            return new BasicAudioPlaylist("Search results for: " + query, null, null, null, null, tracks, null, true);
        }
        return null;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        // No special values to encode
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new SaavnAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager);
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

    private AudioTrack buildTrack(JsonBrowser metadata, String identifier) {
        JsonBrowser info = metadata.get("more_info").isNull() ? metadata : metadata.get("more_info");
        String author;

        if(!info.get("artistMap").get("primary_artists").isNull()) {
            author = info.get("artistMap").get("primary_artists").values().stream()
            .map(e -> e.get("name").text())
            .collect(Collectors.joining(", "));
        } else {
            author = metadata.get("primary_artists").text();
        }

        return new SaavnAudioTrack(new AudioTrackInfo(
            metadata.get("title").text(),
            author,
            (long) (info.get("duration").as(Double.class) * 1000.0),
            identifier,
            false,
            metadata.get("perma_url").text(),
            metadata.get("image").text()
        ), this);
    }
}
