package com.sedmelluq.discord.lavaplayer.source.twitter;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;

public class TwitterHttpContextFilter implements HttpContextFilter {
    private final static Logger log = LoggerFactory.getLogger(TwitterHttpContextFilter.class);

    private String token;
    private String guessToken;

    private final HttpInterfaceManager httpInterfaceManager;

    public TwitterHttpContextFilter(HttpInterfaceManager httpInterfaceManager) {
        this(TwitterConstants.TWITTER_AUTH_KEY, httpInterfaceManager);
    }

    public TwitterHttpContextFilter(String token, HttpInterfaceManager httpInterfaceManager) {
        this.token = token;
        this.httpInterfaceManager = httpInterfaceManager;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public void onContextOpen(HttpClientContext context) {

    }

    @Override
    public void onContextClose(HttpClientContext context) {

    }

    @Override
    public void onRequest(HttpClientContext context, HttpUriRequest request, boolean isRepetition) {
        if (request.getURI().getHost().contains("api.twitter.com")) {
            request.setHeader("Authorization", "Bearer " + this.token);
            request.setHeader("Content-Type", "application/json");
            if (!request.getURI().getPath().contains("guest/activate")) {
                if (guessToken == null || guessToken.isEmpty()) this.fetchGuessToken();
                request.setHeader("x-guest-token", guessToken); //This increase request limit.
            }
        }
    }

    @Override
    public boolean onRequestResponse(HttpClientContext context, HttpUriRequest request, HttpResponse response) {
        if (response.getStatusLine().getStatusCode() == 429) {
            this.fetchGuessToken();
            return true;
        }
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_FORBIDDEN) {
            try {
                JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
                String message = json.get("errors").index(0).get("message").text();
                if (message != null && message.equals("Bad guest token.")) {
                    this.fetchGuessToken();
                    return true;
                }
            } catch (IOException e) {}
        }
        return false;
    }

    @Override
    public boolean onRequestException(HttpClientContext context, HttpUriRequest request, Throwable error) {
        return false;
    }

    private void fetchGuessToken() {
        try (
            HttpInterface httpInterface = httpInterfaceManager.getInterface();
            CloseableHttpResponse response = httpInterface.execute(new HttpPost(TwitterConstants.AUTH_URL))
        ) {
            HttpClientTools.assertSuccessWithContent(response, "guest token");
            JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
            
            String guessToken = json.get("guest_token").text();
            if (guessToken == null || guessToken.isEmpty()) {
                throw new IOException("Guess token not found on auth page.");
            }

            this.guessToken = guessToken;
        } catch (IOException e) {
            log.error("Failed to fetch Twitter guest token.", e);
        }
    }
}
