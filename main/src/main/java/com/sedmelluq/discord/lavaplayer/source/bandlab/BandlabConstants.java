package com.sedmelluq.discord.lavaplayer.source.bandlab;

public class BandlabConstants {
    public static final String BANDLAB_API = "https://www.bandlab.com/api/v1.3";

    public static final String COLLECTION_BANDLAB_API = BANDLAB_API + "/collections/%s";
    public static final String SONG_POST_BANDLAB_API = BANDLAB_API + "/users/%s/songs/%s";
    public static final String ALBUM_BANDLAB_API = BANDLAB_API + "/albums/%s";

    public static final String COLLECTION_URI = "https://www.bandlab.com/%s/collections/%s";
    public static final String ALBUM_URI = "https://www.bandlab.com/%s/albums/%s";
    public static final String TRACK_URI = ALBUM_URI + "/tracks/%s";
}