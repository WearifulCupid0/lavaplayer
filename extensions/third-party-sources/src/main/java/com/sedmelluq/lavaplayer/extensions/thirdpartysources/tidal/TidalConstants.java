package com.sedmelluq.lavaplayer.extensions.thirdpartysources.tidal;

public class TidalConstants {
    static final String AUTH_URL = "https://auth.tidal.com/v1/oauth2/token";
    static final String DEFAULT_COUNTRY_CODE = "US";
    static final String DEFAULT_LOCALE = "en-US";

    //Main endpoints
    static final String MAIN_URL = "https://www.tidal.com";

    static final String TRACK_URL = MAIN_URL + "/track/";
    static final String ALBUM_URL = MAIN_URL + "/album/";
    static final String ARTIST_URL = MAIN_URL + "/artist/";
    static final String PLAYLIST_URL = MAIN_URL + "/playlist/";

    //Api endpoints
    static final String API_URL = "https://openapi.tidal.com/v2";

    static final String SEARCH_API_URL = API_URL + "/searchResults/%s/relationships/tracks";
    static final String TRACK_API_URL = API_URL + "/tracks";
    static final String ALBUM_API_URL = API_URL + "/albums";
    static final String PLAYLIST_API_URL = API_URL + "/playlists";
    static final String ARTIST_API_TRACKS = API_URL + "/artists";
}
