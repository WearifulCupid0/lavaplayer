package com.sedmelluq.discord.lavaplayer.source.saavn;

public class SaavnConstants {
    static final String MAIN_DOMAIN = "https://www.jiosaavn.com";
    static final String API_URL = MAIN_DOMAIN + "/api.php?_format=json&_marker=0";

    static final String SONG_URL = MAIN_DOMAIN + "/song/%s/%s";
    static final String ALBUM_URL = MAIN_DOMAIN + "/album/%s/%s";
    static final String PLAYLIST_URL = MAIN_DOMAIN + "/featured/%s/%s";
}
