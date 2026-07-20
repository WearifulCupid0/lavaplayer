package com.sedmelluq.lavaplayer.source.bandlab;

public class BandlabConstants {
    static final String BANDLAB_URL = "https://www.bandlab.com";
    static final String TRACK_URL = BANDLAB_URL +  "/track/%s";
    static final String ARTIST_URL = BANDLAB_URL + "/%s";
    static final String ALBUM_URL = BANDLAB_URL + "/%s/albums/%s";
    static final String PLAYLIST_URL = BANDLAB_URL + "/%s/collections/%s";

    static final String OAUTH_URL = "https://accounts.bandlab.com/oauth/connect/token";

    static final String BANDLAB_API_URL = "https://www.bandlab.com/api/v1.3";

    static final String BANDLAB_API_SEARCH = BANDLAB_API_URL + "/search/songs";
    static final String BANDLAB_API_TRACK = BANDLAB_API_URL + "/songs/%s";
    static final String BANDLAB_API_REVISION = BANDLAB_API_URL + "/revisions/%s";
    static final String BANDLAB_API_POST = BANDLAB_API_URL + "/posts/%s";
    static final String BANDLAB_API_USER = BANDLAB_API_URL + "/users/%s";
    static final String BANDLAB_API_USER_TRACKS = BANDLAB_API_URL + "/users/%s/track-posts";
    static final String BANDLAB_API_ALBUM = BANDLAB_API_URL + "/albums/%s";
    static final String BANDLAB_API_PLAYLIST = BANDLAB_API_URL + "/collections/%s";
}
