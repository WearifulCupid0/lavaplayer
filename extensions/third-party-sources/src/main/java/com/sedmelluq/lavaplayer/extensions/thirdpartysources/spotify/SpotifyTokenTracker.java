package com.sedmelluq.lavaplayer.extensions.thirdpartysources.spotify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class SpotifyTokenTracker {
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";

    private final String clientId;
    private final String clientSecret;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String accessToken;
    private volatile long expiresAtMillis;

    private final HttpInterfaceManager httpInterfaceManager;

    public SpotifyTokenTracker(HttpInterfaceManager httpInterfaceManager, String clientId, String clientSecret) {
        this.clientId = firstNonBlank(clientId, getPropertyOrEnv("SPOTIFY_CLIENT_ID"));
        this.clientSecret = firstNonBlank(clientSecret, getPropertyOrEnv("SPOTIFY_CLIENT_SECRET"));
        this.httpInterfaceManager = httpInterfaceManager;
    }

    public synchronized String getToken() {
        long now = System.currentTimeMillis();

        if (accessToken != null && now < expiresAtMillis - 60_000) {
            return accessToken;
        }

        refreshToken();
        return accessToken;
    }

    private void refreshToken() {
        try {
            HttpPost request = new HttpPost(TOKEN_URL);

            String credentials = clientId + ":" + clientSecret;
            String encodedCredentials = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            request.setHeader("Authorization", "Basic " + encodedCredentials);
            request.setHeader("Content-Type", "application/x-www-form-urlencoded");

            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("grant_type", "client_credentials"));

            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpInterfaceManager.getInterface().execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                if (statusCode < 200 || statusCode >= 300) {
                    throw new IllegalStateException("Spotify token request failed with status " + statusCode + ": " + body);
                }

                JsonNode json = mapper.readTree(body);

                this.accessToken = json.get("access_token").asText();
                int expiresIn = json.get("expires_in").asInt();

                this.expiresAtMillis = Instant.now()
                        .plusSeconds(Math.max(60, expiresIn))
                        .toEpochMilli();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to refresh Spotify access token.", e);
        }
    }

    private static String getPropertyOrEnv(String name) {
        String property = System.getProperty(name);

        if (!isBlank(property)) {
            return property;
        }

        return System.getenv(name);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }

        return null;
    }
}