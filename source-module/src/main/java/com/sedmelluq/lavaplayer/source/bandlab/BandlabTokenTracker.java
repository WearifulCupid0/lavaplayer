package com.sedmelluq.lavaplayer.source.bandlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BandlabTokenTracker {
    private static final Logger log = LoggerFactory.getLogger(BandlabTokenTracker.class);

    private final String refreshToken;

    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String accessToken;
    private volatile long expiresAtMillis;

    private final HttpInterfaceManager httpInterfaceManager;

    public BandlabTokenTracker(String refreshToken, HttpInterfaceManager httpInterfaceManager) {
        if (refreshToken == null || refreshToken.isBlank())
            throw new RuntimeException("Bandlab refresh token can't be null");

        this.refreshToken = refreshToken;
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
            log.debug("Updating bandlab access token (current is: {})", this.accessToken);
            HttpPost request = new HttpPost(BandlabConstants.OAUTH_URL);

            request.setHeader("Content-Type", "application/x-www-form-urlencoded");

            List<NameValuePair> params = new ArrayList<>();

            params.add(new BasicNameValuePair("client_id", "bandlab_web"));
            params.add(new BasicNameValuePair("redirect_uri", "https://www.bandlab.com/auth/bandlab/callback"));
            params.add(new BasicNameValuePair("grant_type", "refresh_token"));
            params.add(new BasicNameValuePair("refresh_token", refreshToken));

            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpInterfaceManager.getInterface().execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                if (statusCode < 200 || statusCode >= 300) {
                    throw new IllegalStateException("Bandlab token request failed with status " + statusCode + ": " + body);
                }

                JsonNode json = mapper.readTree(body);

                this.accessToken = json.get("access_token").asText();
                int expiresIn = json.get("expires_in").asInt();

                this.expiresAtMillis = Instant.now()
                        .plusSeconds(Math.max(60, expiresIn))
                        .toEpochMilli();

                log.debug("Updating Bandlab access token succeeded, new access token is {}", this.accessToken);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to refresh Bandlab access token.", e);
        }
    }
}
