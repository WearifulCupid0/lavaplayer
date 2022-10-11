package com.sedmelluq.discord.lavaplayer.source.deezer;

public class DeezerConstants {
    static final String SHARE_URL = "https://deezer.page.link/";
    static final String API_URL = "https://api.deezer.com/2.0";
    static final String AJAX_URL = "https://www.deezer.com/ajax/gw-light.php";
    static final String MEDIA_URL = "https://media.deezer.com/v1/get_url";
    static final String BLOWFISH = "g4el58wc0zvf9na1";
    static final String MEDIA_PAYLOAD = "{\"license_token\":\"%s\",\"media\": [{\"type\": \"FULL\",\"formats\": [{\"cipher\": \"BF_CBC_STRIPE\", \"format\": \"MP3_128\"}]}],\"track_tokens\": [\"%s\"]}";
}
