package com.sedmelluq.lavaplayer.source.tunein;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio track that handles processing TuneIn radio tracks.
 */
public class TuneinAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(TuneinAudioTrack.class);

    private static final String API_URL =
            "https://opml.radiotime.com/Tune.ashx?render=json&formats=any&id=";

    private static final int MAX_REFERENCE_REDIRECTS = 8;

    private final TuneinAudioSourceManager sourceManager;

    /**
     * @param trackInfo Track info.
     * @param sourceManager Source manager which was used to find this track.
     */
    public TuneinAudioTrack(AudioTrackInfo trackInfo, TuneinAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            String playbackUrl = loadPlaybackUrl(httpInterface);

            log.debug("Resolved TuneIn radio {} to stream URL: {}", trackInfo.identifier, playbackUrl);

            AudioTrack delegate = loadDelegateTrack(playbackUrl);

            if (!(delegate instanceof InternalAudioTrack)) {
                throw new FriendlyException(
                        "TuneIn stream resolved to a non-playable track type.",
                        SUSPICIOUS,
                        null
                );
            }

            processDelegate((InternalAudioTrack) delegate, localExecutor);
        }
    }

    private String loadPlaybackUrl(HttpInterface httpInterface) throws IOException {
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(API_URL + trackInfo.identifier))) {
            HttpClientTools.assertSuccessWithContent(response, "TuneIn radio API");

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            JsonBrowser body = json.get("body");

            if (json.isNull() || body.isNull() || !body.isList()) {
                throw new IOException("TuneIn API response does not contain a stream list.");
            }

            String playbackUrl = selectBestStreamUrl(body);

            if (playbackUrl == null || playbackUrl.isBlank()) {
                throw new IOException("There are no available streams for this TuneIn radio.");
            }

            return playbackUrl;
        }
    }

    private String selectBestStreamUrl(JsonBrowser body) {
        String bestUrl = null;
        long bestScore = Long.MIN_VALUE;

        for (JsonBrowser stream : body.values()) {
            String url = firstNonBlank(
                    stream.get("url").text(),
                    stream.get("URL").text()
            );

            if (url == null) {
                continue;
            }

            long score = scoreStream(stream, url);

            if (score > bestScore) {
                bestScore = score;
                bestUrl = url;
            }
        }

        return bestUrl;
    }

    private long scoreStream(JsonBrowser stream, String url) {
        String format = firstNonBlank(
                stream.get("formats").text(),
                stream.get("format").text(),
                stream.get("media_type").text(),
                stream.get("mediaType").text()
        );

        long bitrate = stream.get("bitrate").asLong(0);
        long reliability = stream.get("reliability").asLong(0);

        long score = 0;

        if (format != null) {
            String normalizedFormat = format.toLowerCase();

            if (normalizedFormat.contains("mp3")) {
                score += 3000;
            } else if (normalizedFormat.contains("aac")) {
                score += 2500;
            } else if (normalizedFormat.contains("ogg")) {
                score += 2000;
            } else if (normalizedFormat.contains("m3u") || normalizedFormat.contains("hls")) {
                score += 1500;
            }
        }

        String normalizedUrl = url.toLowerCase();

        if (normalizedUrl.contains(".mp3")) {
            score += 300;
        } else if (normalizedUrl.contains(".aac")) {
            score += 250;
        } else if (normalizedUrl.contains(".m3u8") || normalizedUrl.contains(".m3u")) {
            score += 150;
        } else if (normalizedUrl.contains(".pls")) {
            score += 100;
        }

        score += Math.min(bitrate, 500);
        score += Math.min(reliability, 100);

        return score;
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
                    throw new IOException("TuneIn stream resolved to an empty reference.");
                }

                log.debug("Following TuneIn stream reference to: {}", reference.identifier);
                continue;
            }

            if (item == null) {
                throw new IOException("TuneIn stream URL was not recognised by the HTTP source manager: " +
                        reference.identifier);
            }

            throw new IOException("TuneIn stream resolved to an unsupported audio item: " +
                    item.getClass().getName());
        }

        throw new IOException("Too many redirects while resolving TuneIn stream URL.");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new TuneinAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public TuneinAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}