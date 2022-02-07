package com.sedmelluq.lavaplayer.extensions.thirdpartysources.tidal;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TidalTokenTracker {
    private static final Logger log = LoggerFactory.getLogger(TidalTokenTracker.class);

    private static final String MAIN_PAGE_URL = "https://listen.tidal.com";
    private static final long TOKEN_REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(1);
    private static final String PAGE_APP_SCRIPT_REGEX = "src=\"/app\\.([a-zA-Z0-9-_]+)\\.js\"";
    private static final String APP_SCRIPT_TOKEN_REGEX = "tp\\(\\)\\?\"(?:[a-zA-Z0-9-_]+)\":\"([a-zA-Z0-9-_]+)\"";

    private static final Pattern pageAppScriptPattern = Pattern.compile(PAGE_APP_SCRIPT_REGEX);
    private static final Pattern appScriptTokenPattern = Pattern.compile(APP_SCRIPT_TOKEN_REGEX);

    private final Object tokenLock = new Object();
    private final HttpInterfaceManager httpInterfaceManager;
    private String token;
    private long lastTokenUpdate;

    public TidalTokenTracker(HttpInterfaceManager httpInterfaceManager) {
        this.httpInterfaceManager = httpInterfaceManager;
    }

    /**
     * Updates the Token if more than {@link #TOKEN_REFRESH_INTERVAL} time has passed since last updated.
     */
    public void updateToken() {
        synchronized (tokenLock) {
            long now = System.currentTimeMillis();
            if (now - lastTokenUpdate < TOKEN_REFRESH_INTERVAL) {
                log.debug("TIDAL token was recently updated, not updating again right away.");
                return;
            }

            lastTokenUpdate = now;
            log.info("Updating TIDAL token (current is {}).", token);

            try {
                token = findTokenFromSite();
                log.info("Updating TIDAL token succeeded, new ID is {}.", token);
            } catch (Exception e) {
                log.error("TIDAL token update failed.", e);
            }
        }
    }

    public String getToken() {
        synchronized (tokenLock) {
            if (token == null) {
                updateToken();
            }

            return token;
        }
    }

    private String findTokenFromSite() throws IOException {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            String id = findApplicationScriptId(httpInterface);
            return findTokenFromScriptId(id, httpInterface);
        }
    }

    private String findApplicationScriptId(HttpInterface httpInterface) throws IOException {
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(MAIN_PAGE_URL))) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("Invalid status code for main page response: " + statusCode);
            }

            String page = EntityUtils.toString(response.getEntity());
            Matcher scriptMatcher = pageAppScriptPattern.matcher(page);

            if (scriptMatcher.find()) {
                return scriptMatcher.group(1);
            } else {
                throw new IllegalStateException("Could not find application script from main page.");
            }
        }
    }

    private String findTokenFromScriptId(String id, HttpInterface httpInterface) throws IOException {
        String url = MAIN_PAGE_URL + "/app." + id + ".js";
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(url))) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("Invalid status code for script page response: " + statusCode);
            }

            String page = EntityUtils.toString(response.getEntity());
            Matcher tokenMatcher = appScriptTokenPattern.matcher(page);

            if (tokenMatcher.find()) {
                return tokenMatcher.group(1);
            } else {
                throw new IllegalStateException("Could not find application token from script page.");
            }
        }
    }
}