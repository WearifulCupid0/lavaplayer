package com.sedmelluq.lavaplayer.source.bandlab;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BandlabUrlParser {
    private static final String BASE_URL =
            "^(?:https?://)?(?:www\\.)?bandlab\\.com";

    private static final String END =
            "/?(?:[?#].*)?$";

    private static final String UUID =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static final String POST_ID =
            "[0-9a-f]{32}_[0-9a-f]{32}";

    private static final String SLUG =
            "[^/?#]+";

    public static final Pattern ALBUM_URL = Pattern.compile(
            BASE_URL + "/(?<artistSlug>" + SLUG + ")/albums/(?<id>" + UUID + ")" + END,
            Pattern.CASE_INSENSITIVE
    );

    public static final Pattern PLAYLIST_URL = Pattern.compile(
            BASE_URL + "/(?<artistSlug>" + SLUG + ")/collections/(?<id>" + UUID + ")" + END,
            Pattern.CASE_INSENSITIVE
    );

    public static final Pattern SONG_URL = Pattern.compile(
            BASE_URL + "/songs/(?<id>[^/?#]+)" + END,
            Pattern.CASE_INSENSITIVE
    );

    public static final Pattern TRACK_URL = Pattern.compile(
            BASE_URL + "/track/(?<id>[^/?#]+)" + END,
            Pattern.CASE_INSENSITIVE
    );

    public static final Pattern REVISION_URL = Pattern.compile(
            BASE_URL + "/revision/(?<id>" + UUID + ")" + END,
            Pattern.CASE_INSENSITIVE
    );

    public static final Pattern ARTIST_URL = Pattern.compile(
            BASE_URL + "/(?<artistSlug>(?!(?:track|revision|api)(?:/|$))" + SLUG + ")(?:/[^?#]*)?" + END,
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern UUID_ID = Pattern.compile(
            "^" + UUID + "$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern POST_ID_PATTERN = Pattern.compile(
            "^" + POST_ID + "$",
            Pattern.CASE_INSENSITIVE
    );

    public static BandlabUrlData parse(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        String input = url.trim();

        Matcher albumMatcher = ALBUM_URL.matcher(input);
        if (albumMatcher.matches()) {
            return new BandlabUrlData(
                    BandlabUrlType.ALBUM,
                    albumMatcher.group("id"),
                    albumMatcher.group("artistSlug")
            );
        }

        Matcher playlistMatcher = PLAYLIST_URL.matcher(input);
        if (playlistMatcher.matches()) {
            return new BandlabUrlData(
                    BandlabUrlType.PLAYLIST,
                    playlistMatcher.group("id"),
                    playlistMatcher.group("artistSlug")
            );
        }

        Matcher songMatcher = SONG_URL.matcher(input);
        if (songMatcher.matches()) {
            return new BandlabUrlData(
                    BandlabUrlType.SONG,
                    songMatcher.group("id"),
                    null
            );
        }

        Matcher trackMatcher = TRACK_URL.matcher(input);
        if (trackMatcher.matches()) {
            String revId = getQueryParameter(input, "revId");

            if (revId != null && UUID_ID.matcher(revId).matches()) {
                return new BandlabUrlData(
                        BandlabUrlType.REVISION,
                        revId,
                        null
                );
            }

            String id = trackMatcher.group("id");

            if (POST_ID_PATTERN.matcher(id).matches()) {
                return new BandlabUrlData(
                        BandlabUrlType.POST,
                        id,
                        null
                );
            }

            if (UUID_ID.matcher(id).matches()) {
                return new BandlabUrlData(
                        BandlabUrlType.SONG,
                        id,
                        null
                );
            }

            return null;
        }

        Matcher revisionMatcher = REVISION_URL.matcher(input);
        if (revisionMatcher.matches()) {
            return new BandlabUrlData(
                    BandlabUrlType.REVISION,
                    revisionMatcher.group("id"),
                    null
            );
        }

        Matcher artistMatcher = ARTIST_URL.matcher(input);
        if (artistMatcher.matches()) {
            return new BandlabUrlData(
                    BandlabUrlType.ARTIST,
                    null,
                    artistMatcher.group("artistSlug")
            );
        }

        return null;
    }

    private static String getQueryParameter(String url, String parameterName) {
        int queryStart = url.indexOf('?');

        if (queryStart == -1) {
            return null;
        }

        int fragmentStart = url.indexOf('#', queryStart);
        String query = fragmentStart == -1
                ? url.substring(queryStart + 1)
                : url.substring(queryStart + 1, fragmentStart);

        if (query.isEmpty()) {
            return null;
        }

        String[] pairs = query.split("&");

        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }

            int equalsIndex = pair.indexOf('=');

            String name;
            String value;

            if (equalsIndex == -1) {
                name = pair;
                value = "";
            } else {
                name = pair.substring(0, equalsIndex);
                value = pair.substring(equalsIndex + 1);
            }

            name = urlDecode(name);

            if (parameterName.equalsIgnoreCase(name)) {
                return urlDecode(value);
            }
        }

        return null;
    }

    private static String urlDecode(String value) {
        if (value == null) {
            return null;
        }

        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public enum BandlabUrlType {
        ARTIST,
        ALBUM,
        PLAYLIST,
        SONG,
        POST,
        REVISION
    }

    public static class BandlabUrlData {
        private final BandlabUrlType type;
        private final String id;
        private final String artistSlug;

        public BandlabUrlData(BandlabUrlType type, String id, String artistSlug) {
            this.type = type;
            this.id = id;
            this.artistSlug = artistSlug;
        }

        public BandlabUrlType getType() {
            return type;
        }

        public String getId() {
            return id;
        }

        public String getArtistSlug() {
            return artistSlug;
        }

        public boolean isArtist() {
            return type == BandlabUrlType.ARTIST;
        }

        public boolean hasId() {
            return id != null && !id.trim().isEmpty();
        }
    }
}