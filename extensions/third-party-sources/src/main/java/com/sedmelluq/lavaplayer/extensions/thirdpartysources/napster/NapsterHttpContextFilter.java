package com.sedmelluq.lavaplayer.extensions.thirdpartysources.napster;

import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;

import static com.sedmelluq.lavaplayer.extensions.thirdpartysources.napster.NapsterConstants.DEFAULT_API_KEY;

public class NapsterHttpContextFilter implements HttpContextFilter {
    private final String key;

    public NapsterHttpContextFilter() {
        this(DEFAULT_API_KEY);
    }

    public NapsterHttpContextFilter(String key) {
        this.key = key;
    }

    public String getKey() {
        return this.key;
    }

    @Override
    public void onContextOpen(HttpClientContext context) {

    }

    @Override
    public void onContextClose(HttpClientContext context) {

    }

    @Override
    public void onRequest(HttpClientContext context, HttpUriRequest request, boolean isRepetition) {
        if (request.getURI().getHost().contains("api.napster.com")) {
            try {
                URI uri = new URIBuilder(request.getURI())
                    .setParameter("apikey", this.key)
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
        return false;
    }

    @Override
    public boolean onRequestException(HttpClientContext context, HttpUriRequest request, Throwable error) {
        return false;
    }
}