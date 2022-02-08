package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.client.config.RequestConfig;

import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.*;

public class MixcloudAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private final String TRACK_REGEX = "(?:http://|https://|)?(?:(?:www|beta|m)\\.)?mixcloud\\.com/([^/]+)/([^/]+)";
    private final String PLAYLIST_REGEX = "(?:http://|https://|)?(?:(?:www|beta|m)\\.)?mixcloud\\.com/([^/]+)/playlists/([^/]+)";
    private final String ARTIST_REGEX = "(?:http://|https://|)?(?:(?:www|beta|m)\\.)?mixcloud\\.com/([^/]+)";
    
    private final String SEARCH_PREFIX = "mxsearch:";

    private final Pattern trackPattern = Pattern.compile(TRACK_REGEX);
    private final Pattern playlistPattern = Pattern.compile(PLAYLIST_REGEX);
    private final Pattern artistPattern = Pattern.compile(ARTIST_REGEX);

    private final boolean allowSearch;

    public final MixcloudDataReader dataReader;
    public final MixcloudFormatHandler formatHandler;
    private final HttpInterfaceManager httpInterfaceManager;

    /**
     * Create an instance.
     */
    public MixcloudAudioSourceManager() {
        this(true);
    }

    public MixcloudAudioSourceManager(boolean allowSearch) {
        this(allowSearch, new DefaultMixcloudDataReader(), new DefaultMixcloudFormatHandler());
    }

    public MixcloudAudioSourceManager(
        boolean allowSearch,
        MixcloudDataReader dataReader,
        MixcloudFormatHandler formatHandler
    ) {
        this.allowSearch = allowSearch;

        this.dataReader = dataReader;
        this.formatHandler = formatHandler;

        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "mixcloud";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        Matcher matcher;

        if((matcher = playlistPattern.matcher(reference.identifier)).find()) {
            return loadPlaylist(matcher.group(2), matcher.group(1));
        }

        if((matcher = trackPattern.matcher(reference.identifier)).find()) {
            return loadTrack(matcher.group(2), matcher.group(1));
        }

        if((matcher = artistPattern.matcher(reference.identifier)).find()) {
            return loadPlaylist(null, matcher.group(1));
        }

        if(allowSearch && reference.identifier.startsWith(SEARCH_PREFIX)) {
            return loadSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
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
        return new MixcloudAudioTrack(trackInfo, this);
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

    private AudioItem loadTrack(String slug, String username) {
        return MixcloudHelper.requestGraphql(getHttpInterface(), String.format(TRACK_PAYLOAD, slug, username), (json) -> {
            return buildTrack(json.get("cloudcast"));
        });
    }

    private AudioItem loadPlaylist(String slug, String username) {
        if(slug == null && username != null) return loadArtist(username);
        return MixcloudHelper.requestGraphql(getHttpInterface(), String.format(PLAYLIST_PAYLOAD, slug, username), (json) -> {
            JsonBrowser playlistData = json.get("playlist");
            if(playlistData.isNull()) return AudioReference.NO_TRACK;

            List<AudioTrack> tracks = new ArrayList<>();

            playlistData.get("items").get("edges").values()
            .forEach(edge -> {
                JsonBrowser trackData = edge.get("node").get("cloudcast");
                AudioTrack track = buildTrack(trackData);
                if (track != null) tracks.add(track);
            });

            return new BasicAudioPlaylist(
                playlistData.get("name").text(),
                playlistData.get("owner").get("displayName").text(),
                playlistData.get("picture").get("url").text(),
                String.format(PLAYLIST_URL, playlistData.get("owner").get("username").text(), playlistData.get("slug").text()),
                "playlist", tracks, null, false
            );
        });
    }

    private AudioItem loadArtist(String username) {
        return MixcloudHelper.requestGraphql(getHttpInterface(), String.format(ARTIST_PAYLOAD, username), (json) -> {
            JsonBrowser artistData = json.get("user");
            if(artistData.isNull()) return AudioReference.NO_TRACK;

            List<AudioTrack> tracks = new ArrayList<>();

            artistData.get("uploads").get("edges").values()
            .forEach(edge -> {
                JsonBrowser trackData = edge.get("node");
                AudioTrack track = buildTrack(trackData);
                if (track != null) tracks.add(track);
            });

            return new BasicAudioPlaylist(
                artistData.get("displayName").text(),
                artistData.get("displayName").text(),
                artistData.get("picture").get("url").text(),
                String.format(ARTIST_URL, artistData.get("username").text()),
                "artist", tracks, null, false
            );
        });
    }

    private AudioItem loadSearch(String query) {
        return MixcloudHelper.requestGraphql(getHttpInterface(), String.format(SEARCH_PAYLOAD, query.replaceAll("\"|\\\\", "")), (json) -> {
            JsonBrowser edges = json.get("viewer").get("search").get("searchQuery").get("cloudcasts").get("edges");
            
            if(edges.index(0).isNull()) return AudioReference.NO_TRACK;

            List<AudioTrack> tracks = new ArrayList<>();

            edges.values()
            .forEach(edge -> {
                JsonBrowser trackData = edge.get("node");
                AudioTrack track = buildTrack(trackData);
                if (track != null) tracks.add(track);
            });

            return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
        });
    }

    private AudioTrack buildTrack(JsonBrowser trackData) {
        if(trackData.isNull()) return null;
        if(dataReader.isTrackPlayable(trackData)) {
            List<MixcloudTrackFormat> formats = dataReader.readTrackFormats(getHttpInterface(), trackData);
            MixcloudTrackFormat bestFormat = formatHandler.chooseBestFormat(formats);
            String identifier = formatHandler.buildFormatIdentifier(bestFormat);
            if (identifier != null) return new MixcloudAudioTrack(dataReader.readTrackInfo(trackData, identifier), this);
        }
        return null;
    }
}