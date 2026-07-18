package com.sedmelluq.lavaplayer.source.audiomack;

import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;

import java.net.URI;

public class AudiomackHttpContextFilter implements HttpContextFilter {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36";

    private final AudiomackTokenTracker tokenTracker;

    public AudiomackHttpContextFilter(AudiomackTokenTracker tokenTracker) {
        if (tokenTracker == null) {
            throw new IllegalArgumentException("Audiomack token tracker cannot be null.");
        }

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
        URI uri = request.getURI();

        if (uri == null || !tokenTracker.shouldSign(uri)) {
            return;
        }

        if (!request.containsHeader(HttpHeaders.USER_AGENT)) {
            request.setHeader(HttpHeaders.USER_AGENT, USER_AGENT);
        }

        request.removeHeaders(HttpHeaders.AUTHORIZATION);

        String authorization = tokenTracker.createAuthorizationHeader(request.getMethod(), uri);
        request.setHeader(HttpHeaders.AUTHORIZATION, authorization);
    }

    @Override
    public boolean onRequestResponse(
            HttpClientContext context,
            HttpUriRequest request,
            HttpResponse response
    ) {
        return false;
    }

    @Override
    public boolean onRequestException(
            HttpClientContext context,
            HttpUriRequest request,
            Throwable error
    ) {
        return false;
    }
}