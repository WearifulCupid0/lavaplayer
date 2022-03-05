package com.sedmelluq.lavaplayer.extensions.thirdpartysources.napster;

public class NapsterConstants {
    //Main
    static final String APP_URL = "https://app.napster.com";

    static final String TRACK_URL = APP_URL + "/track/";
    static final String ALBUM_URL = APP_URL + "/album/";
    static final String ARTIST_URL = APP_URL + "/artist/";
    static final String PLAYLIST_URL = APP_URL + "/playlist/";

    //API
    static final String DEFAULT_API_KEY = "YTkxZTRhNzAtODdlNy00ZjMzLTg0MWItOTc0NmZmNjU4Yzk4";

    static final String BASE_URL = "https://api.napster.com";
    static final String API_URL = BASE_URL + "/v2.2";

    static final String ALBUM_API_URL = API_URL + "/albums/%s/%s";
    static final String ALBUM_TRACK_API_URL = API_URL + "/albums/%s/tracks?limit=200";
    static final String ALBUM_ID_API_URL = API_URL + "/albums/%s";
    static final String TRACK_API_URL = API_URL + "/tracks/%s/%s/%s";
    static final String TRACK_ID_API_URL = API_URL + "/tracks/%s";
    static final String ARTIST_API_URL = API_URL + "/artists/%s";
    static final String ARTIST_TRACK_API_URL = ARTIST_API_URL + "/tracks?limit=200";
    static final String PLAYLIST_API_URL = API_URL + "/playlists/%s";
    static final String PLAYLIST_TRACK_API_URL = PLAYLIST_API_URL + "/tracks?limit=200";
    static final String SEARCH_API_URL = API_URL + "/search";

    //CDN
    static final String CDN_URL = "https://direct.rhapsody.com/imageserver/images/%s/500x500.png";
}
