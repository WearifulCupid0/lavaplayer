package com.sedmelluq.lavaplayer.extensions.thirdpartysources.deezer;

import com.sedmelluq.discord.lavaplayer.container.*;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.SourceTools;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;

public class DeezerPodcastAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(DeezerPodcastAudioTrack.class);

    private static final int MAX_REFERENCE_REDIRECTS = 8;

    private final DeezerAudioSourceManager sourceManager;

    public DeezerPodcastAudioTrack(
            AudioTrackInfo trackInfo,
            DeezerAudioSourceManager sourceManager
    ) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }

    private String getToken(HttpInterface httpInterface) throws IOException {
        HttpGet request = new HttpGet(DeezerConstants.AJAX_URL + "?method=deezer.getUserData&input=3&api_version=1.0&api_token=");

        JsonBrowser json = DeezerAudioSourceManager.fetchResponseAsJson(httpInterface, request);
        DeezerAudioSourceManager.checkResponse(json, "Failed to get user token");

        return  json.get("results").get("checkForm").text();
    }

    private String getMediaUrl(HttpInterface httpInterface, String apiToken) throws IOException, URISyntaxException {
        HttpPost getTrackToken = new HttpPost(DeezerConstants.AJAX_URL + "?method=episode.getData&input=3&api_version=1.0&api_token=" + apiToken);
        getTrackToken.setEntity(new StringEntity("{\"episode_id\":\"" + this.trackInfo.identifier + "\"}", ContentType.APPLICATION_JSON));

        JsonBrowser trackTokenJson = DeezerAudioSourceManager.fetchResponseAsJson(httpInterface, getTrackToken);
        DeezerAudioSourceManager.checkResponse(trackTokenJson, "Failed to get podcast stream url");

        JsonBrowser results = trackTokenJson.get("results");

        String mediaUrl = results.get("EPISODE_DIRECT_STREAM_URL").text();

        if (SourceTools.isBlank(mediaUrl))
            throw new IOException("Failed to get deezer podcast stream url.");

        return mediaUrl;
    }

    private AudioTrack loadDelegateTrack(String playbackUrl) throws IOException {
        AudioReference reference = new AudioReference(playbackUrl, trackInfo.title);

        for (int redirectCount = 0; redirectCount < MAX_REFERENCE_REDIRECTS; redirectCount++) {
            AudioItem item = sourceManager.loadStream(reference);

            if (item instanceof AudioTrack) {
                return (AudioTrack) item;
            }

            if (item instanceof AudioReference) {
                reference = (AudioReference) item;

                if (reference.identifier == null) {
                    throw new IOException("Deezer podcast stream resolved to an empty reference.");
                }

                log.debug("Following Deezer podcast stream reference to: {}", reference.identifier);
                continue;
            }

            if (item == null) {
                throw new IOException("Deezer podcast stream URL was not recognised by the HTTP source manager: " +
                        reference.identifier);
            }

            throw new IOException("Deezer podcast stream resolved to an unsupported audio item: " +
                    item.getClass().getName());
        }

        throw new IOException("Too many redirects while resolving Deezer podcast stream URL.");
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {

        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String arl = null;
            try {
                Object userData = getUserData();
                if (userData != null) {
                    JsonBrowser jsonUserData = JsonBrowser.parse(userData.toString());
                    if (jsonUserData.get("arl") != null) {
                        arl = jsonUserData.get("arl").text();
                    }
                }
            } catch (IOException e) {
                log.debug("Failed to parse arl from userData", e);
            }

            if (arl == null) {
                arl = this.sourceManager.getTokenTracker().getArl();
            }
            BasicCookieStore cookieStore = new BasicCookieStore();
            httpInterface.getContext().setCookieStore(cookieStore);
            httpInterface.getContext().setRequestConfig(
                    RequestConfig.copy(httpInterface.getContext().getRequestConfig())
                            .setCookieSpec(CookieSpecs.STANDARD)
                            .build()
            );

            BasicClientCookie cookie = new BasicClientCookie("arl", arl);
            cookie.setPath("/");
            cookie.setSecure(true);
            cookie.setDomain("deezer.com");
            cookie.setAttribute("domain", ".deezer.com");
            cookieStore.addCookie(cookie);

            String apiToken = getToken(httpInterface);
            String mediaURI = getMediaUrl(httpInterface, apiToken);

            AudioTrack delegateTrack = loadDelegateTrack(mediaURI);

            processDelegate((InternalAudioTrack) delegateTrack, executor);
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new DeezerPodcastAudioTrack(
                trackInfo,
                sourceManager
        );
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
