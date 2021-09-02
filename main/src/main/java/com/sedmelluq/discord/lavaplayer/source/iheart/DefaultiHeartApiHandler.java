package com.sedmelluq.discord.lavaplayer.source.iheart;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class DefaultiHeartApiHandler implements iHeartApiHandler {
    private final HttpInterfaceManager httpInterfaceManager;

    public DefaultiHeartApiHandler() {
        this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
    }

    @Override
    public ExtendedHttpConfigurable getHttpConfiguration() {
        return httpInterfaceManager;
    }

    @Override
    public AudioTrack radio(String id, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI uri = new URIBuilder("https://api.iheart.com/api/v2/content/liveStations").addParameter("id", id).build();
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "radio response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (!json.get("hits").isNull() && !json.get("hits").index(0).isNull()) {
                    iHeartAudioTrack track = trackFactory.apply(buildRadio(json.get("hits").index(0)));
                    track.setRichInfo(buildRadioRich(json.get("hits").index(0)));
                    return track;
                }
                return null;
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load iHeart radio", SUSPICIOUS, e);
        }
    }

    @Override
    public AudioPlaylist search(String query, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI uri = new URIBuilder("https://api.iheart.com/api/v2/content/liveStations").addParameter("q", query).build();
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "search results response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (!json.get("hits").isNull() && !json.get("hits").index(0).isNull()) {
                    List<AudioTrack> tracks = new ArrayList<>();
                    json.get("hits").values()
                    .forEach(hit -> {
                        iHeartAudioTrack track = trackFactory.apply(buildRadio(hit));
                        track.setRichInfo(buildRadioRich(hit));
                        tracks.add();
                    });
                    return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
                }
                return null;
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load iHeart search results", SUSPICIOUS, e);
        }
    }

    @Override
    public AudioPlaylist podcast(String id, String episodeId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            URI uri = URI.create("https://api.iheart.com/api/v3/podcast/podcasts/" + id);
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "podcast response");
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if(json.get("error").isNull()) {
                    uri = URI.create("https://api.iheart.com/api/v3/podcast/podcasts/" + id + "/episodes");
                    try (CloseableHttpResponse episodes = httpInterface.execute(new HttpGet(uri))) {
                        HttpClientTools.assertSuccessWithContent(episodes, "podcast episodes response");
                        JsonBrowser eps = JsonBrowser.parse(episodes.getEntity().getContent());
                        if(!eps.get("data").isNull() && !eps.get("data").index(0).isNull()) {
                            List<AudioTrack> tracks = new ArrayList<>();
                            String podcastName = json.get("title").text();
                            eps.get("data").values()
                            .forEach(ep -> {
                                iHeartAudioTrack track = trackFactory.apply(buildEpisode(ep, podcastName));
                                track.setRichInfo(buildEpisodeRich(ep));
                                tracks.add(track);
                            });
                            String url = String.format("https://www.iheart.com/podcast/%s/", json.get("slug").text());
                            return new BasicAudioPlaylist(
                                podcastName, podcastName,
                                tracks.get(0).getInfo().artwork,
                                url, "podcast", tracks,
                                findSelectedTrack(tracks, episodeId), false
                            );
                        }
                    }
                }
                return null;
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load iHeart podcast", SUSPICIOUS, e);
        }
    }

    private AudioTrack findSelectedTrack(List<AudioTrack> tracks, String id) {
        if (id != null) {
            for (AudioTrack track : tracks) {
                if (id.equals(track.getIdentifier())) {
                    return track;
                }
            }
        }
    
        return null;
    }

    private AudioTrackInfo buildRadio(JsonBrowser radio) {
        return new AudioTrackInfo(
            radio.get("description").text(),
            radio.get("name").text(),
            Units.DURATION_MS_UNKNOWN,
            radio.get("id").text(),
            true,
            radio.get("link").text(),
            radio.get("logo").text()
        );
    }

    private JSONObject buildRadioRich(JsonBrowser radio) {
        JSONObject json = new JSONObject();

        if (!radio.get("band").isNull()) json.put("band", radio.get("band").text());
        if (!radio.get("callLetters").isNull()) json.put("callLetters", radio.get("callLetters").text());
        if (!radio.get("freq").isNull()) json.put("frequency", radio.get("freq").text());
        if (!radio.get("cume").isNull()) json.put("cume", radio.get("cume").as(Double.class));
        if (!radio.get("countries").isNull()) json.put("countryCode", radio.get("countries").text());
        if (!radio.get("markets").isNull() && !radio.get("markets").values().isEmpty()) json.put("city", radio.get("markets").index(0).get("city").text());

        return json.length() > 0 ? json : null;
    }

    private AudioTrackInfo buildEpisode(JsonBrowser episode, String podcastName) {
        String id = episode.get("id").text();
        return new AudioTrackInfo(
            episode.get("title").text(),
            podcastName,
            (long) (episode.get("duration").as(Double.class) * 1000.0),
            id,
            false,
            String.format("https://www.iheart.com/podcast/%s/episode/%s/", episode.get("podcastId").text(), id),
            episode.get("imageUrl").text()
        );
    }

    private JSONObject buildEpisodeRich(JsonBrowser episode) {
        JSONObject json = new JSONObject();

        if (!episode.get("isExplicit").isNull()) json.put("explicit", episode.get("isExplicit").as(Boolean.class));
        if (!episode.get("description").isNull()) json.put("description", episode.get("description").text());

        return json.length() > 0 ? json : null;
    }
}
