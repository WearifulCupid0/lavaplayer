package com.sedmelluq.lavaplayer.extensions.thirdpartysources.applemusic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppleMusicTokenTracker {
    private static final String APPLE_MUSIC_HOME = "https://music.apple.com/us/browse";
    private static final long REFRESH_EARLY_MILLIS = 10 * 60 * 1000L;

    private static final Pattern JWT_PATTERN = Pattern.compile(
            "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"
    );

    private static final Pattern SCRIPT_URL_PATTERN = Pattern.compile(
            "<script[^>]+src=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpInterfaceManager httpInterfaceManager;

    private volatile String token;
    private volatile long expiresAtMillis;

    public AppleMusicTokenTracker(HttpInterfaceManager httpInterfaceManager) {
        this.httpInterfaceManager = httpInterfaceManager;
    }

    public synchronized String getToken() {
        long now = System.currentTimeMillis();

        if (token != null && now < expiresAtMillis - REFRESH_EARLY_MILLIS) {
            return token;
        }

        refreshToken();
        return token;
    }

    private void refreshToken() {
        try {
            String configuredToken = System.getenv("APPLE_MUSIC_DEVELOPER_TOKEN");

            if (configuredToken != null && !configuredToken.isBlank()) {
                TokenCandidate candidate = validateToken(configuredToken.trim());

                this.token = candidate.token;
                this.expiresAtMillis = candidate.expiresAtMillis;
                return;
            }

            String html = get(APPLE_MUSIC_HOME);
            Set<String> candidates = new LinkedHashSet<>();

            collectJwtCandidates(html, candidates);

            Document document = Jsoup.parse(html, APPLE_MUSIC_HOME);

            for (Element script : document.select("script[src]")) {
                String scriptUrl = script.absUrl("src");

                if (scriptUrl == null || scriptUrl.isBlank()) {
                    continue;
                }

                if (!isProbablyAppleMusicScript(scriptUrl)) {
                    continue;
                }

                try {
                    String scriptBody = get(scriptUrl);
                    collectJwtCandidates(scriptBody, candidates);

                    TokenCandidate tokenCandidate = findValidToken(candidates);

                    if (tokenCandidate != null) {
                        this.token = tokenCandidate.token;
                        this.expiresAtMillis = tokenCandidate.expiresAtMillis;
                        return;
                    }
                } catch (Exception ignored) {
                    // Keep trying other scripts. Apple changes bundles frequently.
                }
            }

            TokenCandidate tokenCandidate = findValidToken(candidates);

            if (tokenCandidate != null) {
                this.token = tokenCandidate.token;
                this.expiresAtMillis = tokenCandidate.expiresAtMillis;
                return;
            }

            throw new IllegalStateException("Could not extract Apple Music developer token from music.apple.com.");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to refresh Apple Music developer token.", e);
        }
    }

    private String get(String url) throws Exception {
        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        request.setHeader("Accept-Language", "en-US,en;q=0.9,pt-BR;q=0.8,pt;q=0.7");
        request.setHeader("Referer", "https://music.apple.com/");
        request.setHeader("Origin", "https://music.apple.com");

        try (CloseableHttpResponse response = httpInterfaceManager.getInterface().execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String body = response.getEntity() != null
                    ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                    : "";

            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException("Request to " + url + " failed with status " + statusCode);
            }

            return body;
        }
    }

    private void collectJwtCandidates(String text, Set<String> candidates) {
        if (text == null || text.isBlank()) {
            return;
        }

        Matcher matcher = JWT_PATTERN.matcher(text);

        while (matcher.find()) {
            candidates.add(matcher.group());
        }
    }

    private TokenCandidate findValidToken(Set<String> candidates) {
        for (String candidate : candidates) {
            try {
                return validateToken(candidate);
            } catch (Exception ignored) {
                // Try next candidate.
            }
        }

        return null;
    }

    private TokenCandidate validateToken(String candidate) throws Exception {
        String[] parts = candidate.split("\\.");

        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format.");
        }

        JsonNode header = decodeJwtJson(parts[0]);
        JsonNode payload = decodeJwtJson(parts[1]);

        String algorithm = header.path("alg").asText("");

        if (!"ES256".equals(algorithm)) {
            throw new IllegalArgumentException("Not an Apple Music ES256 token.");
        }

        long expiresAtSeconds = payload.path("exp").asLong(0);

        if (expiresAtSeconds <= 0) {
            throw new IllegalArgumentException("Token has no expiration.");
        }

        long expiresAtMillis = expiresAtSeconds * 1000L;

        if (expiresAtMillis <= System.currentTimeMillis() + REFRESH_EARLY_MILLIS) {
            throw new IllegalArgumentException("Token is expired or too close to expiration.");
        }

        return new TokenCandidate(candidate, expiresAtMillis);
    }

    private JsonNode decodeJwtJson(String value) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        return mapper.readTree(decoded);
    }

    private boolean isProbablyAppleMusicScript(String url) {
        String lower = url.toLowerCase();

        return lower.contains("music.apple.com")
                || lower.contains("amp-api")
                || lower.contains("assets")
                || lower.endsWith(".js");
    }

    private static class TokenCandidate {
        private final String token;
        private final long expiresAtMillis;

        private TokenCandidate(String token, long expiresAtMillis) {
            this.token = token;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}