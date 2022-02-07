package com.sedmelluq.lavaplayer.extensions.thirdpartysources.spotify;

import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;

public class SpotifyHttpContextFilter implements HttpContextFilter {
    private final SpotifyTokenTracker tokenTracker;

    public SpotifyHttpContextFilter(SpotifyTokenTracker tokenTracker) {
        this.tokenTracker = tokenTracker;
    }

    @Override
    public void onContextOpen(HttpClientContext context) {

    }

    @Override
    public void onContextClose(HttpClientContext context) {

    }

    @Override
    public void onRequest(HttpClientContext context, HttpUriRequest request, boolean isRepetition) {
        if (request.getURI().getHost().contains("api.spotify.com")) {
            request.setHeader("Authorization", tokenTracker.getFormmatedToken());
            request.setHeader("Content-Type", "application/json");
        }
    }

    @Override
    public boolean onRequestResponse(HttpClientContext context, HttpUriRequest request, HttpResponse response) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
            tokenTracker.updateToken();
            return true;
        }
        return false;
    }

    @Override
    public boolean onRequestException(HttpClientContext context, HttpUriRequest request, Throwable error) {
        return false;
    }
}