package com.sedmelluq.lavaplayer.extensions.thirdpartysources.applemusic;

import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;

public class AppleMusicHttpContextFilter implements HttpContextFilter {
    private final AppleMusicTokenTracker tokenTracker;

    public AppleMusicHttpContextFilter(AppleMusicTokenTracker tokenTracker) {
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
        if (request.getURI().getHost().contains("api.music.apple.com")) {
            request.setHeader("Authorization", tokenTracker.getFormmatedToken());
            request.setHeader("Origin", "https://music.apple.com");
            request.setHeader("Referer", "https://music.apple.com/");
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