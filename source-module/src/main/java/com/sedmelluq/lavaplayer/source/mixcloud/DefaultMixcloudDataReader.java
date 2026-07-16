package com.sedmelluq.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackAuthorInfo;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sedmelluq.lavaplayer.source.mixcloud.MixcloudConstants.DECRYPTION_KEY;
//import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.MANIFEST_AUDIO_URL;
import static com.sedmelluq.lavaplayer.source.mixcloud.MixcloudConstants.HLS_AUDIO_URL;

public class DefaultMixcloudDataReader implements MixcloudDataReader {
    private static final Logger log = LoggerFactory.getLogger(DefaultMixcloudDataReader.class);

    @Override
    public boolean isTrackPlayable(JsonBrowser trackData) {
        return !trackData.get("isExclusive").asBoolean(false) || trackData.get("streamStatus").safeText().equals("LIVE");
    }

    @Override
    public AudioTrackInfo readTrackInfo(JsonBrowser trackData, String identifier) {
        AudioTrackAuthorInfo artistInfo = new AudioTrackAuthorInfo(
                trackData.get("owner").get("displayName").safeText(),
                String.format(MixcloudConstants.ARTIST_URL, trackData.get("owner").get("username").safeText())
        );
        return new AudioTrackInfo(
            trackData.get("name").safeText(),
            artistInfo,
            (long) (trackData.get("audioLength").as(Double.class) * 1000.0),
            identifier,
            false,
            trackData.get("url").text(),
            MixcloudConstants.CDN_URL + trackData.get("picture").get("urlRoot").text(),
            !trackData.get("restrictedReason").isNull(),
            null
        );
    }

    @Override
    public AudioTrackInfo readLiveInfo(JsonBrowser liveData, String identifier) {
        String artistUsername = liveData.get("owner").get("username").safeText();
        AudioTrackAuthorInfo artistInfo = new AudioTrackAuthorInfo(
                liveData.get("owner").get("displayName").safeText(),
                String.format(MixcloudConstants.ARTIST_URL, artistUsername)
        );
        return new AudioTrackInfo(
                liveData.get("name").safeText(),
                artistInfo,
                Units.DURATION_MS_UNKNOWN,
                identifier,
                true,
                String.format(MixcloudConstants.LIVE_URL, artistUsername),
                MixcloudConstants.CDN_URL + liveData.get("thumbnailUrl").text(),
                null
        );
    }
    
    @Override
    public List<MixcloudTrackFormat> readTrackFormats(HttpInterface httpInterface, JsonBrowser trackData, boolean isLive) {
        ArrayList<MixcloudTrackFormat> formats = new ArrayList<>();

        if (isLive) {
            formats.add(new DefaultMixcloudTrackFormat("hls", trackData.get("hlsUrl").text()));
            return formats;
        }

        JsonBrowser streamInfo = trackData.get("streamInfo");
        if(!streamInfo.isNull()) {
            if(!streamInfo.get("url").isNull()) {
                formats.add(new DefaultMixcloudTrackFormat("progressive", decodeStreamInfoUrl(streamInfo.get("url").text())));
            }
            //When I add support for fragmented streams I will add this again okay?
            //if(!streamInfo.get("dashUrl").isNull()) {
                //formats.add(new DefaultMixcloudTrackFormat("segments", decodeStreamInfoUrl(streamInfo.get("dashUrl").text())));
            //}
            if(!streamInfo.get("hlsUrl").isNull()) {
                formats.add(new DefaultMixcloudTrackFormat("hls", decodeStreamInfoUrl(streamInfo.get("hlsUrl").text())));
            }
        } else {
            String url = trackData.get("url").text();
            log.debug("StreamInfo Object not found for Mixcloud track {}, starting loading with waveformUrl or previewUrl fields", url);
            String uuid = getStreamId(trackData);
            if(uuid != null) {
                //String manifestUrl = MixcloudHelper.getStreamUrl(httpInterface, String.format(MANIFEST_AUDIO_URL, "%s", uuid));
                //if (manifestUrl != null) formats.add(new DefaultMixcloudTrackFormat("segments", manifestUrl));
                String hlsUrl = MixcloudHelper.getStreamUrl(httpInterface, String.format(HLS_AUDIO_URL, "%s", uuid));
                if (hlsUrl != null) formats.add(new DefaultMixcloudTrackFormat("hls", hlsUrl));
                if (formats.isEmpty()) {
                    log.debug("Stream url not found at Mixcloud track {}, skipping", url);
                    return null;
                }
            } else {
                log.debug("Stream UUID not found at Mixcloud track {}, skipping", url);
                return null;
            }
        }

        return formats;
    }

    private String getStreamId(JsonBrowser trackData) {
        String waveformUrl = trackData.get("waveformUrl").text();
        if(waveformUrl != null) {
            return DataFormatTools.extractBetween(waveformUrl, ".com/", ".json");
        }
        String previewUrl = trackData.get("previewUrl").text();
        if(previewUrl != null) {
            return DataFormatTools.extractBetween(previewUrl, "/previews/", ".mp3");
        }

        return null;
    }

    private String decodeStreamInfoUrl(String encoded) {
        try {
            byte[] xor = Base64.getDecoder().decode(encoded);
            byte[] key = DECRYPTION_KEY.getBytes();

            for (int i = 0; i < xor.length; i++) {
                xor[i] = (byte) (xor[i] ^ key[i % key.length]);
            }

            return new String(xor);
        } catch(Exception e) {
            return null;
        }
    }
}