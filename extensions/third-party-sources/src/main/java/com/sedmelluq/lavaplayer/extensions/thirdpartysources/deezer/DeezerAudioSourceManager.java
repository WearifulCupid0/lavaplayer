package com.sedmelluq.lavaplayer.extensions.thirdpartysources.deezer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.*;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.SourceTools;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.source.ThirdPartyAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackAuthorInfo;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class DeezerAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final Logger log = LoggerFactory.getLogger(DeezerAudioSourceManager.class);

    private static final String DEEZER_URL_REGEX = "^(?:https://|http://|)(?:www\\.|)deezer\\.com/(?:[a-zA-Z]{2}/|)(track|album|playlist|artist|episode|show)/(\\d+)";
    private static final String SHARE_URL = "https://deezer.page.link/";
    private static final String NEW_SHARE_URL = "https://link.deezer.com/";
    private static final Pattern deezerUrlPattern = Pattern.compile(DEEZER_URL_REGEX);
    private static final String SEARCH_PREFIX = "dzsearch:";
    private static final String ISRC_PREFIX = "dzisrc:";

    private final HttpAudioSourceManager streamSourceManager;

    private final HttpInterfaceManager httpInterfaceManager;
    private final boolean allowSearch;

    private final DeezerTokenTracker tokenTracker;
    private DeezerAudioTrack.TrackFormat[] formats;

    public final String masterKey;

    public DeezerAudioSourceManager() {
        this(null, null, new HttpAudioSourceManager());
    }

    public DeezerAudioSourceManager(boolean allowSearch) {
        this(null, null, allowSearch, new HttpAudioSourceManager());
    }

    public DeezerAudioSourceManager(String masterKey, String deezerArl) {
        this(masterKey, deezerArl, true, new HttpAudioSourceManager());
    }

    public DeezerAudioSourceManager(String masterKey, String deezerArl, HttpAudioSourceManager streamSourceManager) {
        this(masterKey, deezerArl, true, streamSourceManager);
    }

    public DeezerAudioSourceManager(
            String masterKey,
            String deezerArl,
            boolean allowSearch,
            HttpAudioSourceManager streamSourceManager
    ) {

        deezerArl = SourceTools.firstNonBlank(deezerArl, SourceTools.getPropertyOrEnv("DEEZER_ARL"));
        this.masterKey = SourceTools.firstNonBlank(masterKey, SourceTools.getPropertyOrEnv("DEEZER_MASTER_KEY"));
        this.allowSearch = allowSearch;

        this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
        this.streamSourceManager = streamSourceManager;

        this.tokenTracker = new DeezerTokenTracker(this, deezerArl);
        this.formats = formats != null && formats.length > 0 ? formats : DeezerAudioTrack.TrackFormat.DEFAULT_FORMATS;
    }

    static void checkResponse(JsonBrowser json, String message) throws IllegalStateException {
        if (json == null) {
            throw new IllegalStateException(message + "No response");
        }
        String error = json.get("error").safeText();
        if (!error.equals("{}") && !error.equals("[]") && !error.equals("null") && !error.isEmpty()) {
            throw new IllegalStateException(message + ": " + error);
        }


        String errors = json.get("errors").safeText();
        if (!errors.equals("[]") && !errors.isEmpty()) {
            throw new IllegalStateException(message + ": " + errors);
        }
    }

    public boolean canPlayNative() {
        return !SourceTools.isBlank(this.masterKey) && !SourceTools.isBlank(this.tokenTracker.getArl());
    }

    @Override
    public String getSourceName() {
        return "deezer";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference) {
        String identifier = reference.identifier;

        if (identifier.startsWith(SEARCH_PREFIX) && allowSearch) {
            return this.loadSearch(identifier.substring(SEARCH_PREFIX.length()).trim());
        }

        if (identifier.startsWith(ISRC_PREFIX)) {
            return this.loadTrackISRC(identifier.substring(ISRC_PREFIX.length()).trim());
        }

        if (identifier.startsWith(NEW_SHARE_URL)) {
            HttpGet request = new HttpGet(identifier);
            request.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());

            try (CloseableHttpResponse response = getHttpInterface().execute(request)) {
                int status = response.getStatusLine().getStatusCode();

                if (status == 302 || status == 301) {
                    String location = response.getFirstHeader("Location").getValue();
                    URI uri = URI.create(location);
                    String query = uri.getRawQuery();

                    if (query != null && !query.isBlank()) {
                        for (String part : query.split("&")) {
                            int separator = part.indexOf('=');

                            if (separator > 0) {
                                String key = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8);
                                String value = URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);

                                if (key.equals("awf") && value.startsWith("https://www.deezer.com/")) {
                                    return this.loadItem(playerManager, new AudioReference(value, reference.title));
                                }
                            }
                        }
                    }
                }

                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (identifier.startsWith(SHARE_URL)) {
            HttpGet request = new HttpGet(identifier);
            request.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());

            try (CloseableHttpResponse response = getHttpInterface().execute(request)) {
                int status = response.getStatusLine().getStatusCode();

                if (status == 302 || status == 301) {
                    String location = response.getFirstHeader("Location").getValue();

                    if (location.startsWith("https://www.deezer.com/")) {
                        return this.loadItem(playerManager, new AudioReference(location, reference.title));
                    }
                }

                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Matcher matcher = deezerUrlPattern.matcher(identifier);

        if (matcher.find()) {
            String id = matcher.group(2);

            switch (matcher.group(1)) {
                case "album":
                    return this.loadAlbum(id);

                case "track":
                    return this.loadTrack(id);

                case "playlist":
                    return this.loadPlaylist(id);

                case "artist":
                    return this.loadArtist(id);

                case "show":
                    return this.loadPodcast(id);

                case "episode":
                    return this.loadEpisode(id);

                default:
                    return null;
            }
        }

        return null;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        boolean podcast = track instanceof DeezerPodcastAudioTrack;

        output.writeBoolean(podcast);
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        boolean podcast = input.readBoolean();

        if (podcast)
            return new DeezerPodcastAudioTrack(trackInfo, this);

        return new DeezerAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager);
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
        streamSourceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
        streamSourceManager.configureBuilder(configurator);
    }

    public AudioItem loadStream(AudioReference reference) {
        return streamSourceManager.loadItem(null, reference);
    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    private AudioItem loadTrackISRC(String isrc) {
        return this.loadTrack("isrc:" + isrc);
    }

    private AudioItem loadTrack(String id) {
        JsonBrowser track = this.requestApi(DeezerConstants.API_URL + "/track/" + id);

        if (track == null || track.get("id").isNull()) {
            return AudioReference.NO_TRACK;
        }

        return buildTrack(track);
    }

    private AudioItem loadEpisode(String id) {
        JsonBrowser episode = this.requestApi(DeezerConstants.API_URL + "/episode/" + id);

        if (episode == null || episode.get("id").isNull()) {
            return AudioReference.NO_TRACK;
        }

        return buildEpisode(episode, episode.get("podcast"));
    }

    private AudioItem loadPodcast(String id) {
        JsonBrowser podcast = this.requestApi(DeezerConstants.API_URL + "/podcast/" + id);

        if (podcast == null || podcast.get("id").isNull()) {
            return AudioReference.NO_TRACK;
        }

        JsonBrowser episodes = this.requestApi(DeezerConstants.API_URL + "/podcast/" + id + "/episodes");

        if (episodes == null || episodes.get("data").isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonBrowser episode : episodes.get("data").values())
            tracks.add(buildEpisode(episode, podcast));


        return new BasicAudioPlaylist(
                podcast.get("title").safeText(),
                null,
                podcast.get("picture_xl").text(),
                podcast.get("link").text(),
                "podcast",
                tracks,
                null,
                false
        );
    }

    private AudioItem loadArtist(String id) {
        try {
            URI uri = new URIBuilder(DeezerConstants.API_URL + "/artist/" + id + "/top")
                    .addParameter("limit", "100")
                    .build();

            JsonBrowser json = this.requestApi(uri);

            if (json == null || json.isNull() || json.get("data").index(0).isNull()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();

            for (JsonBrowser track : json.get("data").values()) {
                tracks.add(buildTrack(track));
            }

            JsonBrowser artist = findArtist(json.get("data").index(0).get("contributors").values(), id);

            return new BasicAudioPlaylist(
                    artist.get("name").safeText(),
                    artist.get("name").safeText(),
                    artist.get("picture_xl").text(),
                    artist.get("link").text(),
                    "artist",
                    tracks,
                    null,
                    false
            );
        } catch (Exception e) {
            throw new FriendlyException(
                    "Failed to load Deezer search result",
                    SUSPICIOUS,
                    e
            );
        }
    }

    private JsonBrowser findArtist(List<JsonBrowser> artists, String id) {
        for (JsonBrowser artist : artists) {
            if (id.equals(artist.get("id").text())) {
                return artist;
            }
        }

        return null;
    }

    private AudioItem loadAlbum(String id) {
        JsonBrowser album = this.requestApi(DeezerConstants.API_URL + "/album/" + id);

        if (album == null || album.get("id").isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();

        for (JsonBrowser track : album.get("tracks").get("data").values()) {
            tracks.add(buildTrack(track));
        }

        return new BasicAudioPlaylist(
                album.get("title").text(),
                album.get("artist").get("name").text(),
                album.get("cover_xl").text(),
                album.get("link").text(),
                album.get("record_type").safeText().toLowerCase(),
                tracks,
                null,
                false
        );
    }

    private AudioItem loadPlaylist(String id) {
        JsonBrowser playlist = this.requestApi(DeezerConstants.API_URL + "/playlist/" + id);

        if (playlist == null || playlist.get("id").isNull()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();

        for (JsonBrowser track : playlist.get("tracks").get("data").values()) {
            tracks.add(buildTrack(track));
        }

        return new BasicAudioPlaylist(
                playlist.get("title").text(),
                playlist.get("creator").get("name").text(),
                playlist.get("picture_xl").text(),
                playlist.get("link").text(),
                "playlist",
                tracks,
                null,
                false
        );
    }

    private AudioItem loadSearch(String query) {
        try {
            URI uri = new URIBuilder(DeezerConstants.API_URL + "/search")
                    .addParameter("limit", "100")
                    .addParameter("q", query)
                    .build();

            JsonBrowser json = this.requestApi(uri);

            if (json == null || json.get("data").values().isEmpty()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();

            for (JsonBrowser track : json.get("data").values()) {
                tracks.add(buildTrack(track));
            }

            return new BasicAudioPlaylist(
                    "Search results for: " + query,
                    null,
                    null,
                    null,
                    "search",
                    tracks,
                    null,
                    true
            );
        } catch (Exception e) {
            throw new FriendlyException(
                    "Failed to load Deezer search result",
                    SUSPICIOUS,
                    e
            );
        }
    }

    private AudioTrack buildTrack(JsonBrowser trackInfo) {
        List<AudioTrackAuthorInfo> artists = new ArrayList<>();

        if (!trackInfo.get("contributors").isNull()) {
            for (JsonBrowser artistInfo : trackInfo.get("contributors").values()) {
                artists.add(new AudioTrackAuthorInfo(
                        artistInfo.get("name").text(),
                        artistInfo.get("link").text()
                ));
            }
        } else {
            JsonBrowser artistInfo = trackInfo.get("artist");

            artists.add(new AudioTrackAuthorInfo(
                    artistInfo.get("name").text(),
                    artistInfo.get("link").text()
            ));
        }

        AudioTrackInfo info = new AudioTrackInfo(
                trackInfo.get("title").safeText(),
                artists,
                trackInfo.get("duration").asLong(0) * 1000,
                trackInfo.get("id").text(),
                false,
                trackInfo.get("link").text(),
                trackInfo.get("album").get("cover_xl").text(),
                trackInfo.get("explicit_lyrics").asBoolean(false),
                trackInfo.get("isrc").text()
        );

        return new DeezerAudioTrack(info, this);
    }

    private AudioTrack buildEpisode(JsonBrowser episodeInfo, JsonBrowser showInfo) {
        String title = episodeInfo.get("title").safeText();
        String identifier = episodeInfo.get("id").text();

        AudioTrackInfo info = new AudioTrackInfo(
                title,
                new AudioTrackAuthorInfo(
                        showInfo.get("title").text(),
                        showInfo.get("link").text()
                ),
                episodeInfo.get("duration").asLong(0) * 1000,
                identifier,
                false,
                "https://www.deezer.com/episode/" + identifier,
                episodeInfo.get("picture").text().replace("180x180-", "1000x1000-")
        );

        return new DeezerPodcastAudioTrack(info, this);
    }

    private JsonBrowser requestApi(String uri) {
        return this.requestApi(URI.create(uri));
    }

    private JsonBrowser requestApi(URI uri) {
        HttpGet get = new HttpGet(uri);
        get.setHeader("Accept", "application/json");

        try (HttpInterface httpInterface = getHttpInterface()) {
            return HttpClientTools.fetchResponseAsJson(httpInterface, get);
        } catch (IOException e) {
            throw new FriendlyException(
                    "Failed to make a request to Deezer Api",
                    SUSPICIOUS,
                    e
            );
        }
    }

    public DeezerAudioTrack.TrackFormat[] getFormats() {
        return this.formats;
    }

    public DeezerTokenTracker getTokenTracker() {
        return this.tokenTracker;
    }

    public static JsonBrowser fetchResponseAsJson(HttpInterface httpInterface, HttpUriRequest request) throws IOException {
        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                log.error("Server responded with not found to '{}'", request.getURI());
                return null;
            } else if (statusCode == HttpStatus.SC_NO_CONTENT) {
                log.error("Server responded with not content to '{}'", request.getURI());
                return null;
            } else if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                log.error("Server responded with an error to '{}'", request.getURI());
                throw new FriendlyException("Server responded with an error.", SUSPICIOUS,
                        new IllegalStateException("Response code from channel info is " + statusCode));
            }

            log.debug("Response from '{}' was successful ", request.getURI());
            return JsonBrowser.parse(response.getEntity().getContent());
        }
    }
}