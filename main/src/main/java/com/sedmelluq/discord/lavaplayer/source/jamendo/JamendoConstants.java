package com.sedmelluq.discord.lavaplayer.source.jamendo;

public class JamendoConstants {
    //Main urls
    final static String ORIGIN_URL = "https://www.jamendo.com";
    final static String TRACK_URL = ORIGIN_URL + "/track/";
    final static String PLAYLIST_URL = ORIGIN_URL + "/playlist/";
    final static String ARTIST_URL = ORIGIN_URL + "/artist/";
    final static String ALBUM_URL = ORIGIN_URL + "/album/";

    //API
    final static String DEFAULT_CLIENT_ID = "c7b47146";

    final static String API_URL = "https://api.jamendo.com/v3.0";
    final static String TRACK_API_URL = API_URL + "/tracks";
    final static String ALBUM_API_URL = API_URL + "/albums/tracks";
    final static String ARTIST_API_URL = API_URL + "/artists/tracks";
    final static String PLAYLIST_API_URL = API_URL + "/playlists/tracks";
}
