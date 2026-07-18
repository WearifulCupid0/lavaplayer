package com.sedmelluq.lavaplayer.extensions.thirdpartysources.tidal;

import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.source.ThirdPartyAudioSourceManager;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;

public class TidalHttpContextFilter implements HttpContextFilter {
    private final TidalTokenTracker tokenTracker;

    public TidalHttpContextFilter(TidalTokenTracker tokenTracker) {
        this.tokenTracker = tokenTracker;
    }

    @Override
    public void onContextOpen(HttpClientContext context) {
        // Nothing to do.
    }

    @Override
    public void onContextClose(HttpClientContext context) {
        // Nothing to do.
    }

    @Override
    public void onRequest(HttpClientContext context, HttpUriRequest request, boolean isRepetition) {
        request.setHeader("User-Agent", ThirdPartyAudioSourceManager.USER_AGENT);

        String host = request.getURI().getHost();

        if (host != null && host.contains("openapi.tidal.com")) {
            request.setHeader("Authorization", tokenTracker.getAuthorizationHeader());
            request.setHeader("Accept", "application/vnd.api+json");
            request.setHeader("Content-Type", "application/vnd.api+json");
            request.setHeader("Origin", "https://tidal.com");
            request.setHeader("Referer", "https://tidal.com/");
        }
    }

    @Override
    public boolean onRequestResponse(HttpClientContext context, HttpUriRequest request, HttpResponse response) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
            return tokenTracker.forceUpdateToken();
        }

        return false;
    }

    @Override
    public boolean onRequestException(HttpClientContext context, HttpUriRequest request, Throwable error) {
        return false;
    }
}