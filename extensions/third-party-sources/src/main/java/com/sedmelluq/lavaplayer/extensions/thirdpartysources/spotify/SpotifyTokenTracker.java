package com.sedmelluq.lavaplayer.extensions.thirdpartysources.spotify;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpotifyTokenTracker {
    private static final Logger log = LoggerFactory.getLogger(SpotifyTokenTracker.class);

    private static final String CREDENTIALS_PAYLOAD = "client_id=%s&client_secret=%s&grant_type=client_credentials";
    private static final String TOKEN_GENERATOR_URL = "https://open.spotify.com/get_access_token?reason=transport&productType=embed";
    private static final String CREDENTIALS_URL = "https://accounts.spotify.com/api/token";
    
    private final HttpInterfaceManager httpInterfaceManager;

    private final String clientSecret;
    private String clientId;

    private String token;
    private String tokenType;
    private long expiresMs;
    private boolean anonymous;

    public SpotifyTokenTracker(HttpInterfaceManager httpInterfaceManager) {
        this(httpInterfaceManager, null, null);
    }

    public SpotifyTokenTracker(HttpInterfaceManager httpInterfaceManager, String clientId, String clientSecret) {
        this.httpInterfaceManager = httpInterfaceManager;

        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getClientId() {
        return this.clientId;
    }

    public String getClientSecret() {
        return this.clientSecret;
    }

    public boolean needUpdate() {
        return token == null || System.currentTimeMillis() >= expiresMs;
    }

    public void updateToken() {
        if(!needUpdate()) {
            log.debug("Spotify access token was recently updated, not updating again right away.");
            return;
        }

        log.info("Updating Spotify access token.");

        try {
            if(this.clientId != null && this.clientSecret != null) updateWithClient();
            else updateWithoutClient();
            log.info("Updating Spotify access token succeeded.");
        } catch (Exception e) {
            log.error("Spotify access token update failed.", e);
        }
    }

    private void updateWithoutClient() throws IOException {
        HttpGet get = new HttpGet(TOKEN_GENERATOR_URL);
        get.setHeader("Content-Type", "application/json");
        get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.59");
        try (CloseableHttpResponse response = httpInterfaceManager.getInterface().execute(get)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("Spotify access token request failed with status code: " + statusCode);
            }

            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            if (json.isNull()) {
                throw new IOException("Spotify access token not found.");
            }

            this.clientId = json.get("clientId").text();
            this.token = json.get("accessToken").text();
            this.tokenType = "Bearer";
            this.expiresMs = json.get("accessTokenExpirationTimestampMs").as(Long.class);
            this.anonymous = json.get("isAnonymous").asBoolean(true);
        }
    }

    private void updateWithClient() throws IOException {
        HttpPost post = new HttpPost(CREDENTIALS_URL);
        post.setHeader("Content-Type", "application/x-www-form-urlencoded");
        post.setEntity(new StringEntity(String.format(CREDENTIALS_PAYLOAD, this.clientId, this.clientSecret)));
        try (CloseableHttpResponse response = httpInterfaceManager.getInterface().execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("Spotify access token request failed with status code: " + statusCode);
            }

            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            if (json.isNull()) {
                throw new IOException("Spotify access token not found.");
            }

            this.token = json.get("access_token").text();
            this.tokenType = json.get("token_type").text();
            this.expiresMs = ((long) (json.get("expires_in").as(Double.class) * 1000.0)) + System.currentTimeMillis();
            this.anonymous = false;
        }
    }

    public String getFormmatedToken() {
        if (this.needUpdate()) this.updateToken();
        return this.tokenType + " " + this.token;
    }

    public String getToken() {
        if (this.needUpdate()) this.updateToken();
        return this.token;
    }

    public String getTokenType() {
        if (this.needUpdate()) this.updateToken();
        return this.tokenType;
    }

    public long getExpiresMs() {
        if (this.needUpdate()) this.updateToken();
        return this.expiresMs;
    }

    public boolean getAnonymous() {
        if (this.needUpdate()) this.updateToken();
        return this.anonymous;
    }
}
