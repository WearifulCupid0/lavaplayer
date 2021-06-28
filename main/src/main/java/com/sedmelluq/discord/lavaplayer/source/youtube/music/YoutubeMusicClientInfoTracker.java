package com.sedmelluq.discord.lavaplayer.source.youtube.music;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sedmelluq.discord.lavaplayer.source.youtube.music.YoutubeMusicConstants.MUSIC_ORIGIN;

public class YoutubeMusicClientInfoTracker {
    private static final Logger log = LoggerFactory.getLogger(YoutubeMusicClientInfoTracker.class);

    private static final long REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(1);

    private final HttpInterfaceManager httpInterfaceManager;

    private long lastUpdate;
    private String apiKey;
    private String clientName;
    private String clientVersion;

    public YoutubeMusicClientInfoTracker(HttpInterfaceManager httpInterfaceManager) {
        this.httpInterfaceManager = httpInterfaceManager;
    }

    private void updateClientInfo() {
        lastUpdate =  System.currentTimeMillis();
        log.info("Updating YoutubeMusic client info (current apiKey is {}, current clientName is {}, current clientVersion is {}).", apiKey, clientName, clientVersion);

        try {
            this.findClientInfoFromSite();
            log.info("Updating YoutubeMusic client info succeeded, new apiKey, clientName and clientVersion is {}, {}, {}", apiKey, clientName, clientVersion);
        } catch (Exception e) {
            log.error("YoutubeMusic client info update failed.", e);
        }
    }

    private void findClientInfoFromSite() throws IOException {
        try (
            HttpInterface httpInterface = httpInterfaceManager.getInterface();
            CloseableHttpResponse response = httpInterface.execute(new HttpGet(MUSIC_ORIGIN))
        ) {
            HttpClientTools.assertSuccessWithContent(response, "music client info response");

            String page = EntityUtils.toString(response.getEntity());

            apiKey = extractApiKey(page);
            clientName = extractClientName(page);
            clientVersion = extractClientVersion(page);

            if(apiKey == null || clientName == null || clientVersion == null) {
                throw new IllegalStateException("Failed to parse client info page from YoutubeMusic.");
            }
        }
    }

    private String extractApiKey(String page) {
        return DataFormatTools.extractBetween(page, "INNERTUBE_API_KEY\":\"", "\",");
    }

    private String extractClientName(String page) {
        return DataFormatTools.extractBetween(page, "INNERTUBE_CLIENT_NAME\":\"", "\",");
    }

    private String extractClientVersion(String page) {
        return DataFormatTools.extractBetween(page, "INNERTUBE_CLIENT_VERSION\":\"", "\",");
    }

    public String getApiKey() {
        if(this.apiKey == null) this.updateClientInfo();
        else if (System.currentTimeMillis() - lastUpdate < REFRESH_INTERVAL) this.updateClientInfo();

        return this.apiKey;
    }

    public String getClientName() {
        if(this.clientName == null) this.updateClientInfo();
        else if (System.currentTimeMillis() - lastUpdate < REFRESH_INTERVAL) this.updateClientInfo();

        return this.clientName;
    }

    public String getClientVersion() {
        if(this.clientVersion == null) this.updateClientInfo();
        else if (System.currentTimeMillis() - lastUpdate < REFRESH_INTERVAL) this.updateClientInfo();

        return this.clientVersion;
    }
}
