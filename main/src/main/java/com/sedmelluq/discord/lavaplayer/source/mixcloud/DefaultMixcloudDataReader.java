package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.DECRYPTION_KEY;
//import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.MANIFEST_AUDIO_URL;
//import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.HLS_AUDIO_URL;

public class DefaultMixcloudDataReader implements MixcloudDataReader {
    private static final Logger log = LoggerFactory.getLogger(DefaultMixcloudDataReader.class);
    private final HttpInterfaceManager httpInterfaceManager;
    
    public DefaultMixcloudDataReader() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

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
            //When I add support for fragmented streams I will add this again okay?
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
                //String manifestUrl = getStreamUrl(String.format(MANIFEST_AUDIO_URL, "%s", uuid));
                //if (manifestUrl != null) formats.add(new DefaultMixcloudTrackFormat("segments", manifestUrl));
                //String hlsUrl = getStreamUrl(String.format(HLS_AUDIO_URL, "%s", uuid));
                //if (hlsUrl != null) formats.add(new DefaultMixcloudTrackFormat("hls", hlsUrl));
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
    
    private String getStreamUrl(String baseUrl) {
        int index = 0;
        String url = null;
        while (index++ < 18) {
            try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
                URI uri = URI.create(String.format(baseUrl, index));
                try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                    HttpClientTools.assertSuccessWithContent(response, "stream response");
                    url = uri.toString();
                    break;
                }
            } catch(Exception e) {
                continue;
            }
        }
        return url;
    }
}