package com.sedmelluq.discord.lavaplayer.source.youtube.music;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sedmelluq.discord.lavaplayer.source.youtube.music.YoutubeMusicConstants.MUSIC_ORIGIN;

public class YoutubeMusicClientInfoTracker {
    private static final Logger log = LoggerFactory.getLogger(YoutubeMusicClientInfoTracker.class);

    private static final long REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(1);
    private static final String INNERTUBE_API_KEY_REGEX = "INNERTUBE_API_KEY\":\"([a-zA-Z0-9-_]+)\",";
    private static final String INNERTUBE_CLIENT_NAME_REGEX = "INNERTUBE_CLIENT_NAME\":\"([a-zA-Z0-9-_]+)\",";
    private static final String INNERTUBE_CLIENT_VERSION_REGEX = "INNERTUBE_CLIENT_VERSION\":\"([0-9\\.]+?)\",";

    private static final Pattern innertubeApiKey = Pattern.compile(INNERTUBE_API_KEY_REGEX);
    private static final Pattern innertubeClientName = Pattern.compile(INNERTUBE_CLIENT_NAME_REGEX);
    private static final Pattern innertubeClientVersion = Pattern.compile(INNERTUBE_CLIENT_VERSION_REGEX);

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

            String page = response.getEntity().toString();
            Matcher apiKeyMatcher = innertubeApiKey.matcher(page);
            Matcher clientNameMatcher = innertubeClientName.matcher(page);
            Matcher clientVersionMatcher = innertubeClientVersion.matcher(page);

            if(!apiKeyMatcher.find() || !clientNameMatcher.find() || !clientVersionMatcher.find()) {
                throw new IllegalStateException("Failed to find client info from main page.");
            }

            this.apiKey = apiKeyMatcher.group(1);
            this.clientName = clientNameMatcher.group(1);
            this.clientVersion = clientVersionMatcher.group(1);
        }
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
