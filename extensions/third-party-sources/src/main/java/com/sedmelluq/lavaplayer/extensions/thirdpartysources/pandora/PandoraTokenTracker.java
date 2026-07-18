package com.sedmelluq.lavaplayer.extensions.thirdpartysources.pandora;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.ThirdPartyAudioSourceManager;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class PandoraTokenTracker {
    private static final Logger log = LoggerFactory.getLogger(PandoraTokenTracker.class);

    private static final String BASE_URL = "https://www.pandora.com";
    private static final String ANONYMOUS_LOGIN_ENDPOINT = "/api/v1/auth/anonymousLogin";
    private static final long DEFAULT_TOKEN_REFRESH_INTERVAL = 24 * 60 * 60 * 1000;

    private final PandoraAudioSourceManager sourceManager;
    private volatile String csrfToken;
    private volatile String authToken;
    private volatile Instant expires;

    public PandoraTokenTracker(PandoraAudioSourceManager sourceManager, String csrfToken) {
        this.sourceManager = sourceManager;
        this.csrfToken = csrfToken;
    }

    public void setCsrfToken(String csrfToken) {
        this.csrfToken = csrfToken;
        this.authToken = null;
        this.expires = null;
    }

    public String getAuthToken() throws IOException {
        if (this.authToken == null || this.expires == null || this.expires.isBefore(Instant.now())) {
            synchronized (this) {
                if (this.authToken == null || this.expires == null || this.expires.isBefore(Instant.now())) {
                    log.debug("Auth token is invalid or expired, refreshing token...");
                    this.refreshAuthToken();
                }
            }
        }
        return this.authToken;
    }

    public void loadCookies(HttpInterface httpInterface) {
        BasicCookieStore cookieStore = new BasicCookieStore();
        httpInterface.getContext().setCookieStore(cookieStore);
        httpInterface.getContext().setRequestConfig(
                RequestConfig.copy(httpInterface.getContext().getRequestConfig())
                        .setCookieSpec(CookieSpecs.STANDARD)
                        .build()
        );

        if (csrfToken == null || csrfToken.isEmpty()) {
            throw new IllegalStateException("CSRF token is required to build cookie header");
        }

        BasicClientCookie cookie = new BasicClientCookie("csrftoken", csrfToken);
        cookie.setPath("/");
        cookie.setSecure(true);
        cookie.setDomain("pandora.com");
        cookie.setAttribute("domain", ".pandora.com");
        cookieStore.addCookie(cookie);
    }

    void refreshAuthToken() throws IOException {
        if (csrfToken == null || csrfToken.isEmpty()) {
            throw new IllegalStateException("CSRF token is required to refresh auth token");
        }

        HttpPost request = new HttpPost(BASE_URL + ANONYMOUS_LOGIN_ENDPOINT);
        HttpInterface httpInterface = sourceManager.getHttpInterface();
        loadCookies(httpInterface);
        request.setHeader("Accept", "application/json, text/plain, */*");
        request.setHeader("accept-language", "en-US,en;q=0.9");
        request.setHeader("Content-Type", "application/json");
        request.setHeader("X-CsrfToken", csrfToken);
        request.setHeader("origin", BASE_URL);
        request.setHeader("User-Agent", ThirdPartyAudioSourceManager.USER_AGENT);
        request.setEntity(new StringEntity("", StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            HttpClientTools.assertSuccessWithContent(response, "pandora token tracker");

            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

            if (json.isNull()) {
                throw new RuntimeException("No response from Pandora anonymous login API");
            }

            if (!json.get("errorCode").isNull()) {
                long errorCode = json.get("errorCode").asLong(-1);
                String errorString = json.get("errorString").text();
                throw new RuntimeException("Error while fetching auth token: " + errorCode + " - " + errorString);
            }
            this.authToken = json.get("authToken").text();
            if (this.authToken == null || this.authToken.isEmpty()) {
                throw new RuntimeException("No auth token received from Pandora API");
            }
            this.expires = Instant.now().plusSeconds(DEFAULT_TOKEN_REFRESH_INTERVAL);
            log.debug("Successfully refreshed Pandora auth token");
        }
    }

    public String getCsrfToken() {
        return csrfToken;
    }

    public synchronized void forceRefresh() {
        this.authToken = null;
        this.expires = null;
    }
}