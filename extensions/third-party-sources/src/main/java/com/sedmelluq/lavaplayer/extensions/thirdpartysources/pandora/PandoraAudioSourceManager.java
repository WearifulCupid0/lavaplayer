package com.sedmelluq.lavaplayer.extensions.thirdpartysources.pandora;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.SourceTools;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.ThirdPartyAudioSourceManager;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.ThirdPartyAudioTrack;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PandoraAudioSourceManager extends ThirdPartyAudioSourceManager implements HttpConfigurable {

    public static final String URL_REGEX = "^@?(?:https?://)?(?:www\\.)?pandora\\.com/(?:playlist/(?<id>PL:[\\d:]+)|artist/[\\w\\-]+(?:/[\\w\\-]+)*/(?<id2>(?:TR|AL|AR)[A-Za-z0-9]+))(?:[?#].*)?$";
    public static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);

    public static final String BASE_URL = "https://www.pandora.com";

    public static final String SEARCH_PREFIX = "pdsearch:";
    public static final String RECOMMENDATIONS_PREFIX = "pdrec:";

    private static final String ENDPOINT_SEARCH = "/api/v3/sod/search";
    private static final String ENDPOINT_ANNOTATE = "/api/v4/catalog/annotateObjects";
    private static final String ENDPOINT_DETAILS = "/api/v4/catalog/getDetails";
    private static final String ENDPOINT_PLAYLIST_TRACKS = "/api/v7/playlists/getTracks";
    private static final String ENDPOINT_ARTIST_ALL_TRACKS = "/api/v4/catalog/getAllArtistTracksWithCollaborations";

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";

    private static final Logger log = LoggerFactory.getLogger(PandoraAudioSourceManager.class);

    private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    private final PandoraTokenTracker tokenTracker;
    private int searchLimit = 6;

    public PandoraAudioSourceManager(AudioPlayerManager audioPlayerManager) {
        this(null, audioPlayerManager);
    }

    public PandoraAudioSourceManager(String csrfToken, AudioPlayerManager audioPlayerManager) {
        this(csrfToken, audioPlayerManager, 50);
    }

    public PandoraAudioSourceManager(String csrfToken, AudioPlayerManager audioPlayerManager, int searchLimit) {
        super(audioPlayerManager, true);

        csrfToken = SourceTools.getStringOrEnv(csrfToken, "PANDORA_CSRF_TOKEN");
        if (SourceTools.isBlank(csrfToken)) {
            throw new IllegalArgumentException("Pandora csrf token must be set");
        }

        this.tokenTracker = new PandoraTokenTracker(this, csrfToken);
        this.searchLimit = searchLimit > 0 ? searchLimit : 6;
    }

    public void setCsrfToken(String csrfToken) {
        if (!SourceTools.isBlank(csrfToken)) {
            this.tokenTracker.setCsrfToken(csrfToken);
        }
    }

    @Override
    public String getSourceName() {
        return "pandora";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;
        try {
            if (identifier.startsWith(SEARCH_PREFIX)) {
                String query = identifier.substring(SEARCH_PREFIX.length());
                if (query.isEmpty()) {
                    throw new IllegalArgumentException("No query provided for search");
                }

                return this.getSearch(query);
            }

            if (identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
                String trackId = identifier.substring(RECOMMENDATIONS_PREFIX.length());
                if (trackId.isEmpty()) {
                    throw new IllegalArgumentException("No track ID provided for recommendations");
                }

                return this.getRecommendations(trackId);
            }

            String input = identifier.trim();
            Matcher matcher = URL_PATTERN.matcher(input);
            if (!matcher.find())
                return null;

            String id = matcher.group("id") != null ? matcher.group("id") : matcher.group("id2");

            if (id == null || id.isEmpty())
                return null;

            if (id.startsWith("TR")) {
                return this.getTrack(id);
            } else if (id.startsWith("AL")) {
                return this.getAlbum(id);
            } else if (id.startsWith("AR")) {
                if (input.contains("/artist/all-songs/")) {
                    return this.getArtistAllSongs(id);
                }
                return this.getArtist(id);
            } else if (id.startsWith("PL:")) {
                return this.getPlaylist(id);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private JsonBrowser postJson(String path, String body) throws IOException {
        return postJsonWithRetry(path, body, false);
    }

    private JsonBrowser postJsonWithRetry(String path, String body, boolean isRetry) throws IOException {
        HttpInterface httpInterface = this.httpInterfaceManager.getInterface();
        this.tokenTracker.loadCookies(httpInterface);

        HttpPost post = new HttpPost(BASE_URL + path);
        post.setHeader("Accept", "application/json, text/plain, */*");
        post.setHeader("accept-language", "en-US,en;q=0.9");
        post.setHeader("Content-Type", "application/json");
        post.setHeader("origin", BASE_URL);
        post.setHeader("sec-fetch-mode", "cors");
        post.setHeader("sec-fetch-site", "same-origin");
        post.setHeader("X-Csrftoken", this.tokenTracker.getCsrfToken());
        post.setHeader("X-Authtoken", this.tokenTracker.getAuthToken());
        post.setHeader("User-Agent", USER_AGENT);
        post.setEntity(new StringEntity(body, StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = httpInterface.execute(post)) {
            HttpClientTools.assertSuccessWithContent(response, "pandora api request");
            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

            if (!isRetry && !json.isNull() && !json.get("errorCode").isNull()) {
                long errorCode = json.get("errorCode").asLong(-1);
                String errorString = json.get("errorString").text();

                if (errorCode == 1001 || (errorCode == 0 && errorString != null && errorString.contains("could not be validated"))) {
                    log.debug("Auth token error detected (code: {}, message: {}), refreshing token and retrying...", errorCode, errorString);
                    this.tokenTracker.forceRefresh();
                    return postJsonWithRetry(path, body, true);
                }
            }

            return json;
        }
    }

    private String getArtworkUrl(JsonBrowser node) {
        JsonBrowser icon = node.get("icon");
        if (!icon.isNull()) {
            String artId = icon.get("artId").text();
            if (artId != null && !artId.isEmpty()) {
                return "https://content-images.p-cdn.com/" + artId + "_1080W_1080H.jpg";
            }
        }

        String thorLayers = node.get("thorLayers").text();
        if (thorLayers != null && !thorLayers.isEmpty()) {
            if (thorLayers.startsWith("_;grid")) {
                String encodedLayers = URLEncoder.encode(thorLayers, StandardCharsets.UTF_8);
                return "https://dyn-images.p-cdn.com/?l=" + encodedLayers + "&w=1080&h=1080";
            }
            return "https://content-images.p-cdn.com/" + thorLayers + "_1080W_1080H.jpg";
        }

        return null;
    }

    private AudioTrack mapTrack(JsonBrowser track, JsonBrowser annotations) {
        String title = track.get("name").text();
        if (title == null || title.isEmpty()) {
            return null;
        }

        List<AudioTrackAuthorInfo> artists = new ArrayList<>();
        String author = track.get("artistName").text();
        if (!SourceTools.isBlank(author)) artists.add(new AudioTrackAuthorInfo(author));
        long duration = track.get("duration").asLong(0) * 1000;
        if (duration == 0) {
            return null;
        }

        String id = track.get("pandoraId").text();
        String urlPath = track.get("shareableUrlPath").text();
        String isrc = track.get("isrc").text();

        String originalUrl = urlPath != null ? BASE_URL + urlPath : null;
        String artworkUrl = getArtworkUrl(track);
        AudioTrackInfo info = new AudioTrackInfo(title, author, duration, id, false, originalUrl, artworkUrl, isrc);

        return new ThirdPartyAudioTrack(info, this);
    }

    private String buildAnnotateRequest(List<String> pandoraIds) {
        StringBuilder ids = new StringBuilder("{\"pandoraIds\":[");
        for (int i = 0; i < pandoraIds.size(); i++) {
            if (i > 0) ids.append(',');
            ids.append('"').append(escape(pandoraIds.get(i))).append('"');
        }

        ids.append("]}");
        return ids.toString();
    }

    private AudioItem getRecommendations(String trackId) throws IOException {
        String detailsBody = "{\"pandoraId\":\"" + escape(trackId) + "\"}";
        JsonBrowser details = postJson(ENDPOINT_DETAILS, detailsBody);
        if (details == null) {
            return AudioReference.NO_TRACK;
        }

        JsonBrowser similar = details.get("trackDetails").get("similarTracks");
        if (similar.isNull() || similar.values().isEmpty()) return AudioReference.NO_TRACK;
        List<String> idList = new ArrayList<>();
        for (JsonBrowser v : similar.values()) {
            idList.add(v.text());
        }

        JsonBrowser annotations = postJson(ENDPOINT_ANNOTATE, buildAnnotateRequest(idList));
        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonBrowser v : similar.values()) {
            JsonBrowser item = annotations.get(v.text());
            if (item.isNull()) continue;
            AudioTrack track = mapTrack(item, annotations);
            if (track != null) tracks.add(track);
        }

        return BasicAudioPlaylist.createRecommendations("Pandora recommendations", tracks);
    }

    private AudioPlaylist parseAlbum(JsonBrowser album, JsonBrowser annotations) {
        String name = album.get("name").text();
        JsonBrowser tracksArray = album.get("tracks");
        List<AudioTrack> tracks = new ArrayList<>();
        if (!tracksArray.isNull()) {
            for (JsonBrowser v : tracksArray.values()) {
                JsonBrowser t = annotations.get(v.text());
                if (t.isNull()) continue;
                AudioTrack at = mapTrack(t, annotations);
                if (at != null) tracks.add(at);
            }
        }
        String url = album.get("shareableUrlPath").text();
        String artworkUrl = getArtworkUrl(album);

        return new BasicAudioPlaylist(name, album.get("artistName").text(), artworkUrl, url != null ? BASE_URL + url : null, "album", tracks, null, false);
    }

    private AudioPlaylist parseArtist(JsonBrowser artist, JsonBrowser detailsRoot) {
        String name = artist.get("name").text();
        List<AudioTrack> tracks = new ArrayList<>();
        JsonBrowser artistDetails = detailsRoot.get("artistDetails");
        JsonBrowser top = artistDetails.get("topTracks");
        JsonBrowser annotations = detailsRoot.get("annotations");

        if (!top.isNull()) {
            for (JsonBrowser v : top.values()) {
                JsonBrowser t = annotations.get(v.text());
                if (t.isNull()) continue;
                AudioTrack at = mapTrack(t, annotations);
                if (at != null) tracks.add(at);
            }
        }

        String url = artist.get("shareableUrlPath").text();
        String artworkUrl = getArtworkUrl(artist);

        return new BasicAudioPlaylist(name + "'s Top Tracks", name, artworkUrl, url != null ? BASE_URL + url : null, "artist", tracks, null, false);
    }

    private AudioPlaylist getPlaylist(String playlistId) throws IOException {
        JsonBrowser request = JsonBrowser.parse("{}");
        JsonBrowser reqObj = JsonBrowser.parse("{}");
        reqObj.put("pandoraId", playlistId);
        reqObj.put("playlistVersion", 0);
        reqObj.put("offset", 0);
        reqObj.put("limit", 5000);
        reqObj.put("annotationLimit", 100);
        reqObj.put("allowedTypes", JsonBrowser.parse("[\"TR\"]"));
        reqObj.put("bypassPrivacyRules", true);
        request.put("request", reqObj);

        JsonBrowser json = postJson(ENDPOINT_PLAYLIST_TRACKS, request.format());
        JsonBrowser annotations = json.get("annotations");
        JsonBrowser tracksNode = json.get("tracks");

        Map<String, JsonBrowser> merged = new HashMap<>();
        for (JsonBrowser v : annotations.values()) {
            String id = v.get("pandoraId").text();
            if (id != null && !id.isEmpty()) {
                merged.put(id, v);
            }
        }

        List<String> allIds = new ArrayList<>();
        for (JsonBrowser t : tracksNode.values()) {
            String id = t.get("pandoraId").text();
            if (id != null && !id.isEmpty()) {
                allIds.add(id);
            }
        }

        List<String> missing = new ArrayList<>();
        for (String id : allIds) {
            if (!merged.containsKey(id)) {
                missing.add(id);
            }
        }

        if (!missing.isEmpty()) {
            JsonBrowser extra = postJson(ENDPOINT_ANNOTATE, buildAnnotateRequest(missing));
            for (String id : missing) {
                JsonBrowser node = extra.get(id);
                if (!node.isNull()) {
                    merged.put(id, node);
                }
            }
        }

        List<AudioTrack> tracks = new ArrayList<>();
        JsonBrowser mergedBrowser = JsonBrowser.parse("{}");
        for (Map.Entry<String, JsonBrowser> entry : merged.entrySet()) {
            mergedBrowser.put(entry.getKey(), entry.getValue());
        }

        for (JsonBrowser t : tracksNode.values()) {
            String id = t.get("pandoraId").text();
            JsonBrowser ann = merged.get(id);
            if (ann == null) continue;
            AudioTrack at = mapTrack(ann, mergedBrowser);
            if (at != null) tracks.add(at);
        }
        String name = json.get("name").text();
        String path = json.get("shareableUrlPath").text();
        String artworkUrl = getArtworkUrl(json);

        String authorName = null;
        String listenerId = json.get("listenerPandoraId").text();
        if (listenerId != null) {
            JsonBrowser author = annotations.get(listenerId);
            if (!author.isNull()) {
                authorName = author.get("fullname").text();
            }
        }

        return new BasicAudioPlaylist(name, authorName, artworkUrl, path != null ? BASE_URL + path : null, "playlist", tracks, null, false);
    }

    public AudioItem getTrack(String trackId) throws IOException {
        String detailsBody = "{\"pandoraId\":\"" + escape(trackId) + "\"}";
        JsonBrowser details = postJson(ENDPOINT_DETAILS, detailsBody);
        if (details == null) return AudioReference.NO_TRACK;

        JsonBrowser annotations = details.get("annotations");
        JsonBrowser track = findByUrlSuffix(trackId, annotations);
        if (track.isNull()) {
            return AudioReference.NO_TRACK;
        }

        AudioTrack at = mapTrack(track, annotations);
        return at != null ? at : AudioReference.NO_TRACK;
    }

    public AudioItem getAlbum(String albumId) throws IOException {
        String detailsBody = "{\"pandoraId\":\"" + escape(albumId) + "\"}";

        JsonBrowser details = postJson(ENDPOINT_DETAILS, detailsBody);
        if (details == null) return AudioReference.NO_TRACK;
        JsonBrowser annotations = details.get("annotations");
        JsonBrowser album = findByUrlSuffix(albumId, annotations);
        if (album.isNull()) {
            return AudioReference.NO_TRACK;
        }

        return parseAlbum(album, annotations);
    }

    public AudioItem getArtist(String artistId) throws IOException {
        String detailsBody = "{\"pandoraId\":\"" + escape(artistId) + "\"}";
        JsonBrowser details = postJson(ENDPOINT_DETAILS, detailsBody);
        if (details == null) return AudioReference.NO_TRACK;
        JsonBrowser annotations = details.get("annotations");
        JsonBrowser artist = findByUrlSuffix(artistId, annotations);
        if (artist.isNull()) {
            return AudioReference.NO_TRACK;
        }
        return parseArtist(artist, details);
    }

    public AudioItem getArtistAllSongs(String artistId) throws IOException {
        String body = "{\"artistPandoraId\":\"" + escape(artistId) + "\",\"annotationLimit\":100}";
        JsonBrowser json = postJson(ENDPOINT_ARTIST_ALL_TRACKS, body);
        if (json == null) return AudioReference.NO_TRACK;

        JsonBrowser annotations = json.get("annotations");
        JsonBrowser tracksNode = json.get("tracks");
        if (tracksNode.isNull() || tracksNode.values().isEmpty()) return AudioReference.NO_TRACK;

        Map<String, JsonBrowser> merged = new HashMap<>();
        for (JsonBrowser v : annotations.values()) {
            String pid = v.get("pandoraId").text();
            if (pid != null && !pid.isEmpty()) {
                merged.put(pid, v);
            }
        }

        List<String> allTrackIds = new ArrayList<>();
        for (JsonBrowser t : tracksNode.values()) {
            String tid = t.text();
            if (tid != null && !tid.isEmpty()) allTrackIds.add(tid);
        }

        List<String> missing = new ArrayList<>();
        for (String tid : allTrackIds) {
            if (!merged.containsKey(tid)) missing.add(tid);
        }
        if (!missing.isEmpty()) {
            JsonBrowser extra = postJson(ENDPOINT_ANNOTATE, buildAnnotateRequest(missing));
            for (String tid : missing) {
                JsonBrowser node = extra.get(tid);
                if (!node.isNull()) merged.put(tid, node);
            }
        }

        JsonBrowser mergedBrowser = JsonBrowser.parse("{}");
        for (Map.Entry<String, JsonBrowser> entry : merged.entrySet()) {
            mergedBrowser.put(entry.getKey(), entry.getValue());
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for (String tid : allTrackIds) {
            JsonBrowser ann = merged.get(tid);
            if (ann == null) continue;
            AudioTrack at = mapTrack(ann, mergedBrowser);
            if (at != null) tracks.add(at);
        }

        JsonBrowser artist = findByUrlSuffix(artistId, annotations);
        if (artist.isNull()) {
            String detailsBody = "{\"pandoraId\":\"" + escape(artistId) + "\"}";
            JsonBrowser details = postJson(ENDPOINT_DETAILS, detailsBody);
            if (details != null) {
                JsonBrowser detailsAnn = details.get("annotations");
                JsonBrowser match = findByUrlSuffix(artistId, detailsAnn);
                if (!match.isNull()) artist = match;
            }
        }
        String name = artist.isNull() ? "All Songs" : (artist.get("name").safeText() + " - All Songs");
        String path = artist.isNull() ? null : artist.get("shareableUrlPath").text();
        String artworkUrl = artist.isNull() ? null : getArtworkUrl(artist);
        String authorName = artist.isNull() ? null : artist.get("name").text();

        return new BasicAudioPlaylist(name, authorName, artworkUrl, path != null ? BASE_URL + path : null, "artist", tracks, null, false);
    }

    private JsonBrowser findByUrlSuffix(String urlTail, JsonBrowser annotations) {
        for (JsonBrowser value : annotations.values()) {
            String path = value.get("shareableUrlPath").text();
            if (path != null && path.endsWith("/" + urlTail)) {
                return value;
            }
            String slug = value.get("slugPlusPandoraId").text();
            if (slug != null && (slug.endsWith(urlTail) || slug.contains(urlTail))) {
                return value;
            }
        }
        return JsonBrowser.NULL_BROWSER;
    }

    public AudioItem getSearch(String query) throws IOException {
        StringBuilder request = new StringBuilder();
        request.append('{')
                .append("\"query\":\"").append(escape(query)).append("\",")
                .append("\"types\":[\"TR\",\"AL\",\"AR\",\"PL\"],")
                .append("\"listener\":null,")
                .append("\"start\":0,")
                .append("\"count\":100,")
                .append("\"annotate\":true,")
                .append("\"annotationRecipe\":\"CLASS_OF_2019\"")
                .append('}');

        JsonBrowser json = postJson(ENDPOINT_SEARCH, request.toString());
        if (json == null) return AudioReference.NO_TRACK;

        JsonBrowser annotations = json.get("annotations");
        JsonBrowser results = json.get("results");
        if (results.isNull() || results.values().isEmpty()) return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = new ArrayList<>();
        int added = 0;

        for (JsonBrowser v : results.values()) {
            JsonBrowser item = annotations.get(v.text());
            if (item.isNull()) continue;
            if (!"TR".equals(item.get("type").text())) continue;
            AudioTrack at = mapTrack(item, annotations);
            if (at != null) {
                tracks.add(at);
                if (++added >= this.searchLimit) break;
            }
        }

        if (tracks.isEmpty()) return AudioReference.NO_TRACK;
        return BasicAudioPlaylist.createSearchResults(query, tracks);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
    }

    @Override
    public void shutdown() {
        try {
            httpInterfaceManager.close();
        } catch (IOException e) {
            log.error("Failed to close HTTP interface manager", e);
        }
    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }
}