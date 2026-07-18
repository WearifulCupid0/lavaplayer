package com.sedmelluq.lavaplayer.extensions.thirdpartysources.tidal;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.SourceTools;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.source.ThirdPartyAudioSourceManager;
import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TidalTokenTracker {
    private static final long REFRESH_EARLY_MILLIS = 60_000L;

    private final Object lock = new Object();
    private final CloseableHttpClient httpClient;

    private volatile String clientId;
    private volatile String clientSecret;

    private String accessToken;
    private String tokenType;
    private long expiresAtMillis;

    public TidalTokenTracker() {
        this(null, null);
    }

    public TidalTokenTracker(String clientId, String clientSecret) {
        this.clientId = SourceTools.firstNonBlank(clientId, SourceTools.getPropertyOrEnv("TIDAL_CLIENT_ID"));
        this.clientSecret = SourceTools.firstNonBlank(clientSecret, SourceTools.getPropertyOrEnv("TIDAL_CLIENT_SECRET"));

        this.httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(10_000)
                        .setSocketTimeout(20_000)
                        .setConnectionRequestTimeout(10_000)
                        .build())
                .disableCookieManagement()
                .build();
    }

    public String getToken() {
        synchronized (lock) {
            long now = System.currentTimeMillis();

            if (accessToken != null && now < expiresAtMillis - REFRESH_EARLY_MILLIS) {
                return accessToken;
            }

            refreshToken();

            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("Could not obtain TIDAL access token.");
            }

            return accessToken;
        }
    }

    public String getAuthorizationHeader() {
        synchronized (lock) {
            String token = getToken();
            String type = SourceTools.firstNonBlank(tokenType, "Bearer");

            return type + " " + token;
        }
    }

    public void setCredentials(String clientId, String clientSecret) {
        synchronized (lock) {
            this.clientId = SourceTools.normalize(clientId);
            this.clientSecret = SourceTools.normalize(clientSecret);

            this.accessToken = null;
            this.tokenType = null;
            this.expiresAtMillis = 0;
        }
    }

    public boolean hasCredentials() {
        return !SourceTools.isBlank(clientId) && !SourceTools.isBlank(clientSecret);
    }

    public boolean forceUpdateToken() {
        synchronized (lock) {
            this.accessToken = null;
            this.tokenType = null;
            this.expiresAtMillis = 0;

            try {
                refreshToken();
                return accessToken != null && !accessToken.isBlank();
            } catch (RuntimeException e) {
                return false;
            }
        }
    }

    public void shutdown() {
        try {
            httpClient.close();
        } catch (IOException ignored) {
            // Ignore close failure.
        }
    }

    private void refreshToken() {
        String id = SourceTools.firstNonBlank(clientId, SourceTools.getPropertyOrEnv("TIDAL_CLIENT_ID"));
        String secret = SourceTools.firstNonBlank(clientSecret, SourceTools.getPropertyOrEnv("TIDAL_CLIENT_SECRET"));

        if (SourceTools.isBlank(id) || SourceTools.isBlank(secret)) {
            throw new IllegalStateException(
                    "TIDAL credentials are not configured. " +
                            "Use new TidalTokenTracker(clientId, clientSecret), setCredentials(...), " +
                            "or set TIDAL_CLIENT_ID and TIDAL_CLIENT_SECRET."
            );
        }

        try {
            HttpPost request = new HttpPost(TidalConstants.AUTH_URL);

            request.setHeader("Accept", "application/json");
            request.setHeader("Content-Type", "application/x-www-form-urlencoded");
            request.setHeader("Origin", "https://tidal.com");
            request.setHeader("Referer", "https://tidal.com/");
            request.setHeader("User-Agent", ThirdPartyAudioSourceManager.USER_AGENT);

            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("client_id", id));
            params.add(new BasicNameValuePair("client_secret", secret));
            params.add(new BasicNameValuePair("grant_type", "client_credentials"));

            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();

                String body = response.getEntity() != null
                        ? IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8)
                        : "";

                if (statusCode < 200 || statusCode >= 300) {
                    throw new IllegalStateException("TIDAL token request failed with status " + statusCode + ": " + body);
                }

                JsonBrowser json = JsonBrowser.parse(body);

                String newAccessToken = json.get("access_token").text();

                if (SourceTools.isBlank(newAccessToken)) {
                    throw new IllegalStateException("TIDAL token response did not contain access_token: " + body);
                }

                long expiresInSeconds = SourceTools.parseLong(json.get("expires_in").text(), 3600L);

                this.accessToken = newAccessToken;
                this.tokenType = SourceTools.firstNonBlank(json.get("token_type").text(), "Bearer");
                this.expiresAtMillis = System.currentTimeMillis() + expiresInSeconds * 1000L;

                this.clientId = id;
                this.clientSecret = secret;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to refresh TIDAL access token.", e);
        }
    }
}