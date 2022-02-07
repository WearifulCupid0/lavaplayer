package com.sedmelluq.lavaplayer.extensions.thirdpartysources.spotify;

public class SpotifyConstants {
    //Public endpoints
    static final String OPEN_URL = "https://open.spotify.com";

    static final String TRACK_URL = OPEN_URL + "/track/";
    static final String ALBUM_URL = OPEN_URL + "/album/";
    static final String PLAYLIST_URL = OPEN_URL + "/playlist/";
    static final String ARTIST_URL = OPEN_URL + "/artist/";

    //API endpoints
    static final String API_URL = "https://api.spotify.com/v1";

    static final String TRACK_API_URL = API_URL + "/tracks/";
    static final String ALBUM_API_URL = API_URL + "/albums/";
    static final String PLAYLIST_API_URL = API_URL + "/playlists/";
    static final String ARTIST_API_URL = API_URL + "/artists/";
    static final String ARTIST_TRACKS_API_URL = ARTIST_API_URL + "%s/top-tracks?market=US";
    static final String SEARCH_API_URL = API_URL + "/search";
}