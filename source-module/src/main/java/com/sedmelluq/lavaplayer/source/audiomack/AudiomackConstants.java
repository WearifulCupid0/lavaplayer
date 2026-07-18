package com.sedmelluq.lavaplayer.source.audiomack;

public class AudiomackConstants {
    static final String AUDIOMACK_URL = "https://audiomack.com";
    static final String AUDIOMACK_ARTIST_URL = AUDIOMACK_URL + "/%s";
    static final String AUDIOMACK_SONG_URL = AUDIOMACK_URL + "/%s/song/%s";
    static final String AUDIOMACK_ALBUM_URL = AUDIOMACK_URL + "/%s/album/%s";
    static final String AUDIOMACK_PLAYLIST_URL = AUDIOMACK_URL + "/%s/playlist/%s";

    static final String AUDIOMACK_API_URl = "https://api.audiomack.com/v1";

    static final String AUDIOMACK_SONG_STREAM = AUDIOMACK_API_URl + "/music/%s/play";

    static final String AUDIOMACK_SONG_RESOLVE = AUDIOMACK_API_URl + "/music/song/%s/%s";
    static final String AUDIOMACK_ALBUM_RESOLVE = AUDIOMACK_API_URl + "/music/album/%s/%s";
    static final String AUDIOMACK_PLAYLIST_RESOLVE = AUDIOMACK_API_URl + "/playlist/%s/%s";
    static final String AUDIOMACK_ARTIST_RESOLVE = AUDIOMACK_API_URl + "/artist/%s/uploads";
    static final String AUDIOMACK_SEARCH = AUDIOMACK_API_URl + "/search";
}
