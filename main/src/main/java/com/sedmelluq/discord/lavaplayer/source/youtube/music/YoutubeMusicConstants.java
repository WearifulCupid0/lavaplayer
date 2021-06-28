package com.sedmelluq.discord.lavaplayer.source.youtube.music;

public class YoutubeMusicConstants {
    static final String MUSIC_ORIGIN = "https://music.youtube.com";
    static final String MUSIC_BASE_URL = MUSIC_ORIGIN + "/youtubei/v1";
    static final String MUSIC_BASE_PAYLOAD = "{\"context\":{\"client\":{\"clientName\":\"%s\",\"clientVersion\":\"%s\"}},";

    static final String MUSIC_SEARCH_URL = MUSIC_BASE_URL + "/search?key=";
    static final String MUSIC_SEARCH_PAYLOAD = MUSIC_BASE_PAYLOAD + "\"query\":\"%s\",\"params\":\"Eg-KAQwIARAAGAAgACgAMABqChADEAQQCRAFEAo=\"}";

    static final String MUSIC_WATCH_URL_PREFIX = MUSIC_ORIGIN + "/watch?v=";
}