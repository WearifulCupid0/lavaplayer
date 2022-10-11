package com.sedmelluq.discord.lavaplayer.source.deezer;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

public class DeezerHttpContextFilter implements HttpContextFilter {
    private static final Logger log = LoggerFactory.getLogger(DeezerHttpContextFilter.class);
    private String apiToken;
    private String sessionId;

    public final DeezerAudioSourceManager audioSourceManager;

    public DeezerHttpContextFilter(DeezerAudioSourceManager audioSourceManager) {
        this.audioSourceManager = audioSourceManager;
    }

    @Override
    public void onContextOpen(HttpClientContext context) {

    }

    @Override
    public void onContextClose(HttpClientContext context) {

    }

    @Override
    public void onRequest(HttpClientContext context, HttpUriRequest request, boolean isRepetition) {
        if (request.getURI().getHost().contains("api.deezer.com")) {
            return;
        } else if (request.getURI().getHost().contains("www.deezer.com")) {
            try {
                URI uri = request.getURI();
                URIBuilder builder = new URIBuilder(request.getURI());

                builder.addParameter("input", "3");
                builder.addParameter("api_version", "1.0");
                builder.addParameter("cid", "550330597");

                if (!uri.toString().contains("deezer.getUserData")) {
                    if (this.sessionId == null || this.apiToken == null) this.getCredentials();
                    builder.addParameter("api_token", this.apiToken);
                    request.setHeader("cookie", "sid=" + this.sessionId);
                }

                if (request instanceof HttpRequestBase) {
                    ((HttpRequestBase) request).setURI(builder.build());
                } else {
                    throw new IllegalStateException("Cannot update request URI.");
                }
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public boolean onRequestResponse(HttpClientContext context, HttpUriRequest request, HttpResponse response) {
        if (request.getURI().getHost().contains("api.deezer.com")) return true;
        try {
            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            if (!json.get("error").get("VALID_TOKEN_REQUIRED").isNull()) {
                this.apiToken = null;
                this.sessionId = null;
                return true;
            }
            return false;
        } catch (Exception e) {
            return this.onRequestException(context, request, e);
        }
    }

    @Override
    public boolean onRequestException(HttpClientContext context, HttpUriRequest request, Throwable error) {
        return false;
    }

    private void getCredentials() {
        HttpPost post = new HttpPost(DeezerConstants.AJAX_URL + "?method=deezer.getUserData");
        try (CloseableHttpResponse response = this.audioSourceManager.getHttpInterface().execute(post)) {
            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            this.sessionId = json.get("results").get("SESSION_ID").text();
            this.apiToken = json.get("results").get("checkForm").text();
            this.audioSourceManager.setLicenseToken(json.get("results").get("USER").get("OPTIONS").get("license_token").text());
            if (this.sessionId == null || this.apiToken == null) throw  new IOException("Failed to fetch new credentials");
        } catch (IOException e) {
            log.error("Failed to update Deezer api token", e);
        }
    }
}
