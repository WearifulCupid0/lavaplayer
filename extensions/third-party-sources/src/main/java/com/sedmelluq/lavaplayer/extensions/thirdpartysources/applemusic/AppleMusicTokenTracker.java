package com.sedmelluq.lavaplayer.extensions.thirdpartysources.applemusic;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.jsoup.nodes.Document;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppleMusicTokenTracker {
    private static final Logger log = LoggerFactory.getLogger(AppleMusicTokenTracker.class);

    private static final String TOKEN_URL = "https://music.apple.com/us/album/sweater-weather/635016635";

    private final ObjectMapper jackson;
    private final HttpInterfaceManager httpInterfaceManager;

    private String token;
    private String tokenType;
    private long expiresMs;

    public AppleMusicTokenTracker(HttpInterfaceManager httpInterfaceManager) {
        this.jackson = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.httpInterfaceManager = httpInterfaceManager;
    }

    public boolean needUpdate() {
        return token == null || System.currentTimeMillis() >= expiresMs;
    }

    public void updateToken() {
        if(token != null && System.currentTimeMillis() >= expiresMs) {
            log.debug("AppleMusic access token was recently updated, not updating again right away.");
            return;
        }

        log.info("Updating AppleMusic access token.");

        try {
            fetchToken();
            log.info("Updating AppleMusic access token succeeded.");
        } catch (Exception e) {
            log.error("AppleMusic access token update failed.", e);
        }
    }

    private void fetchToken() throws Exception {
        HttpGet get = new HttpGet(TOKEN_URL);
        get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.59");
        try (CloseableHttpResponse response = httpInterfaceManager.getInterface().execute(get)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("AppleMusic access token request failed with status code: " + statusCode);
            }

            Document document = Jsoup.parse(response.getEntity().getContent(), StandardCharsets.UTF_8.name(), "https://music.apple.com/");
            JsonBrowser decoded = JsonBrowser.parse(URLDecoder.decode(document.selectFirst("meta[name=desktop-music-app/config/environment]").attr("content"), StandardCharsets.UTF_8));

            this.token = decoded.get("MEDIA_API").get("token").text();
            this.tokenType = "Bearer";
            this.expiresMs = jackson.readTree(Base64.getDecoder().decode(this.token.split("\\.")[1])).get("exp").asLong();
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
}
