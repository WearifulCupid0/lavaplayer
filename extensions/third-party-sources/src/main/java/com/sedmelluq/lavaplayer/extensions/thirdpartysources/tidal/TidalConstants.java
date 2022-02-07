package com.sedmelluq.lavaplayer.extensions.thirdpartysources.tidal;

public class TidalConstants {
    //Main endpoints
    static final String MAIN_URL = "https://www.tidal.com";

    static final String TRACK_URL = MAIN_URL + "/track/";
    static final String ALBUM_URL = MAIN_URL + "/album/";
    static final String PLAYLIST_URL = MAIN_URL + "/playlist/";
    static final String MIX_URL = MAIN_URL + "/mix/";

    //Api endpoints
    static final String API_URL = "https://api.tidal.com/v1";

    static final String SEARCH_API_URL = API_URL + "/search/tracks";
    static final String TRACK_API_URL = API_URL + "/tracks/";
    static final String ALBUM_API_URL = API_URL + "/albums/";
    static final String ALBUM_TRACK_API_URL = API_URL + "/albums/%s/tracks";
    static final String PLAYLIST_API_URL = API_URL + "/playlists/";
    static final String PLAYLIST_TRACK_API_URL = API_URL + "/playlists/%s/tracks";
    static final String MIX_API_URL = API_URL + "/pages/mix";

    //CDN
    static final String CDN_URL = "https://resources.tidal.com/images/%s/640x640.jpg";
}
