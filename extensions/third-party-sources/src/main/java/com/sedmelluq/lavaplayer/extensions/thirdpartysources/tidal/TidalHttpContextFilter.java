package com.sedmelluq.lavaplayer.extensions.thirdpartysources.tidal;

import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import java.net.URI;
import java.net.URISyntaxException;

import com.sedmelluq.lavaplayer.extensions.thirdpartysources.ThirdPartyAudioSourceManager;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;

public class TidalHttpContextFilter implements HttpContextFilter {
    private final TidalTokenTracker tokenTracker;

    public TidalHttpContextFilter(TidalTokenTracker tokenTracker) {
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
        request.setHeader("User-Agent", ThirdPartyAudioSourceManager.USER_AGENT);
        if (request.getURI().getHost().contains("api.tidal.com")) {
            request.setHeader("x-tidal-token", tokenTracker.getToken());
            request.setHeader("Content-Type", "application/json");

            try {
                URI uri = new URIBuilder(request.getURI())
                    .setParameter("limit", "100")
                    .setParameter("countryCode", "US")
                    .build();
                if (request instanceof HttpRequestBase) {
                    ((HttpRequestBase) request).setURI(uri);
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