package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMixcloudDataReader implements MixcloudDataReader {
    private static final Logger log = LoggerFactory.getLogger(DefaultMixcloudDataReader.class);

    private static final String DECRYPTION_KEY = "IFYOUWANTTHEARTISTSTOGETPAIDDONOTDOWNLOADFROMMIXCLOUD";
    //private static final String MANIFEST_AUDIO_URL = "https://audio%s.mixcloud.com/secure/dash2/%s.m4a/manifest.mpd";
    //private static final String HLS_AUDIO_URL = "https://audio%s.mixcloud.com/secure/hls/%s.m4a/index.m3u8";

    @Override
    public boolean isTrackPlayable(JsonBrowser trackData) {
        return !trackData.get("isExclusive").asBoolean(false);
    }

    @Override
    public AudioTrackInfo readTrackInfo(JsonBrowser trackData, String identifier) {
        return new AudioTrackInfo(
            trackData.get("name").safeText(),
            trackData.get("owner").get("displayName").safeText(),
            (long) (trackData.get("audioLength").as(Double.class) * 1000.0),
            identifier,
            false,
            trackData.get("url").text(),
            trackData.get("picture").get("url").text()
        );
    }
    
    @Override
    public List<MixcloudTrackFormat> readTrackFormats(JsonBrowser trackData) {
        ArrayList<MixcloudTrackFormat> formats = new ArrayList<>();

        JsonBrowser streamInfo = trackData.get("streamInfo");
        if(!streamInfo.isNull()) {
            if(!streamInfo.get("url").isNull()) {
                formats.add(new DefaultMixcloudTrackFormat("progressive", decodeStreamInfoUrl(streamInfo.get("url").text())));
            }
            //When I add support for hls and fragmented streams I will add this again okay?
            //if(!streamInfo.get("dashUrl").isNull()) {
                //formats.add(new DefaultMixcloudTrackFormat("segments", decodeStreamInfoUrl(streamInfo.get("dashUrl").text())));
            //}
            //if(!streamInfo.get("hlsUrl").isNull()) {
                //formats.add(new DefaultMixcloudTrackFormat("hls", decodeStreamInfoUrl(streamInfo.get("hlsUrl").text())));
            //}
        } else {
            String url = trackData.get("url").text();
            log.debug("StreamInfo Object not found for Mixcloud track {}, starting loading with waveformUrl or previewUrl fields", url);
            String uuid = getStreamId(trackData);
            if(uuid != null) {
                //String manifestUrl = String.format(MANIFEST_AUDIO_URL, "[1-18]", uuid);
                //formats.add(new DefaultMixcloudTrackFormat("segments", manifestUrl));
                //String hlsUrl = String.format(HLS_AUDIO_URL, "[1-18]", uuid);
                //formats.add(new DefaultMixcloudTrackFormat("hls", hlsUrl));
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