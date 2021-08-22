package com.sedmelluq.discord.lavaplayer.source.dailymotion;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;

import org.apache.http.util.EntityUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DailyMotionAuthorizationManager {
    private static final Logger log = LoggerFactory.getLogger(DailyMotionAuthorizationManager.class);
    private static final String OAUTH_URL = "https://graphql.api.dailymotion.com/oauth/token";
    private static final String OAUTH_PAYLOAD = "client_id=%s&client_secret=%s&grant_type=client_credentials";
    private static final String CLIENT_ID_REGEX = ",\"client_id\":\"([a-zA-Z0-9-_]+)\"";
    private static final String CLIENT_SECRET_REGEX = ",\"client_secret\":\"([a-zA-Z0-9-_]+)\"";

    private static final Pattern clientIdPattern = Pattern.compile(CLIENT_ID_REGEX);
    private static final Pattern clientSecretPattern = Pattern.compile(CLIENT_SECRET_REGEX);

    private String clientId;
    private String clientSecret;

    private String accessToken;
    private Long expiresAt;

    private final HttpInterfaceManager httpInterfaceManager;

    public DailyMotionAuthorizationManager() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    public String getToken() {
        if (accessToken == null || expiresAt <= System.currentTimeMillis()) updateAccessToken();
        return "Bearer " + accessToken;
    }

    private void updateAccessToken() {
        log.info("Updating DailyMotion access token.");
        if (clientId == null || clientSecret == null) updateClientCredentials();
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            HttpPost post = new HttpPost(OAUTH_URL);
            post.setEntity(new StringEntity(String.format(OAUTH_PAYLOAD, clientId, clientSecret)));
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");
            try (CloseableHttpResponse response = httpInterface.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 400) {
                    JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                    if (json.get("error").text() == "invalid_client") {
                        clientId = null; clientSecret = null;
                        updateAccessToken();
                        return;
                    }
                    throw new FriendlyException("Invalid status code for oauth page", FriendlyException.Severity.COMMON, null);
                }
                if (statusCode != 200) {
                    throw new FriendlyException("Invalid status code for oauth page", FriendlyException.Severity.COMMON, null);
                }
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                if (json.get("access_token").isNull()) {
                    throw new FriendlyException("DailyMotion access token was not present.", FriendlyException.Severity.COMMON, null);
                }
                accessToken = json.get("access_token").text();
                expiresAt = System.currentTimeMillis() + (json.get("expires_in").as(Long.class) * 1000);
                log.info("Updating DailyMotion access token succeeded.");
            }
        } catch (Exception e) {
            log.error("Failed to update DailyMotion access token.", e);
        }
    }

    private void updateClientCredentials() {
        log.info("Updating DailyMotion client ID and secret (current is {} and {}).", clientId, clientSecret);
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://dailymotion.com"))) {
                HttpClientTools.assertSuccessWithContent(response, "main page response");
                String responseText = EntityUtils.toString(response.getEntity());
                Matcher clientIdMatcher = clientIdPattern.matcher(responseText);
                if (!clientIdMatcher.find()) {
                    throw new FriendlyException("Failed to find DailyMotion client id from main page.", FriendlyException.Severity.SUSPICIOUS, null);
                }
                Matcher clientSecretMatcher = clientSecretPattern.matcher(responseText);
                if (!clientSecretMatcher.find()) {
                    throw new FriendlyException("Failed to find DailyMotion client secret from main page.", FriendlyException.Severity.SUSPICIOUS, null);
                }
                clientId = clientIdMatcher.group(1);
                clientSecret = clientSecretMatcher.group(1);
                log.info("Updating DailyMotion client ID and SECRET succeeded, new ID and SECRET is {}.", clientId, clientSecret);
            }
        } catch (Exception e) {
            log.error("Failed to update DailyMotion client credentials", e);
        }
    }
}
