package com.sedmelluq.lavaplayer.source.bandlab;

import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;

import java.net.URI;

public class BandlabHttpContextFilter implements HttpContextFilter {
    private final BandlabTokenTracker tokenTracker;

    public BandlabHttpContextFilter(BandlabTokenTracker tokenTracker) {
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
        if (!isBandLabApiRequest(request)) {
            return;
        }

        request.setHeader("Authorization", "Bearer " + tokenTracker.getToken());
        request.setHeader("Accept", "application/json");
        request.setHeader("X-Client-Id", "BandLab-Web");
        request.setHeader("X-Client-Version", "10.2.44");
    }

    @Override
    public boolean onRequestResponse(HttpClientContext context, HttpUriRequest request, HttpResponse response) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
            tokenTracker.getToken();
            return true;
        }
        return false;
    }

    @Override
    public boolean onRequestException(HttpClientContext context, HttpUriRequest request, Throwable error) {
        return false;
    }

    private static boolean isBandLabApiRequest(HttpUriRequest request) {
        if (request == null || request.getURI() == null) {
            return false;
        }

        URI uri = request.getURI();

        String host = uri.getHost();
        String path = uri.getPath();

        if (host == null || path == null) {
            return false;
        }

        return host.equalsIgnoreCase("www.bandlab.com")
                && (path.equals("/api") || path.startsWith("/api/"));
    }
}
