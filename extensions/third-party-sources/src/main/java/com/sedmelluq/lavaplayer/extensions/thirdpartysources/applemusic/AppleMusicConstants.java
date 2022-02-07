package com.sedmelluq.lavaplayer.extensions.thirdpartysources.applemusic;

public class AppleMusicConstants {
    //Main endpoints
    static final String MAIN_URL = "https://music.apple.com/%s";

    static final String ARTIST_URL = MAIN_URL + "/artist/";

    //Api endpoints
    static final String API_URL = "https://api.music.apple.com/v1/catalog/us";

    static final String SEARCH_API_URL = API_URL + "/search";
    static final String TRACK_API_URL = API_URL + "/songs/%s";
    static final String ARTIST_API_URL = API_URL + "/artists/%s";
    static final String ARTIST_TRACK_API_URL = ARTIST_API_URL + "/view/top-songs";
    static final String ALBUM_API_URL = API_URL + "/albums/%s";
    static final String ALBUM_TRACK_API_URL = ALBUM_API_URL + "%s/tracks";
    static final String PLAYLIST_API_URL = API_URL + "/playlists/%s";
    static final String PLAYLIST_TRACK_API_URL = PLAYLIST_API_URL + "%s/tracks";
}
