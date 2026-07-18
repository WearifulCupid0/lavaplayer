package com.sedmelluq.lavaplayer.extensions.thirdpartysources.tidal;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackAuthorInfo;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoBuilder;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.SourceTools;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.source.ThirdPartyAudioTrack;
import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeParseException;

public class TidalHelper {
    public static String findBestArtwork(JsonBrowser artworkFiles) {
        if (!artworkFiles.get("attributes").isNull())
            artworkFiles = artworkFiles.get("attributes");

        if (artworkFiles.get("files").isList())
            artworkFiles = artworkFiles.get("files");

        int resolution = 0;
        String url = null;

        for (JsonBrowser artworkData : artworkFiles.values()) {
            int width = artworkData.get("meta").get("width").asInt(0);
            int height = artworkData.get("meta").get("height").asInt(0);

            int sum = width + height;
            if (resolution < sum) {
                resolution = sum;
                url = artworkData.get("href").text();
            }
        }

        return url;
    }

    public static JsonBrowser findRelationship(JsonBrowser included, String entityId, String entityType) {

        for (JsonBrowser item : included.values())
            if (
                    item.get("id").safeText().equals(entityId) &&
                    item.get("type").safeText().equals(entityType)
            )
                return item;

        return null;
    }

    public static AudioTrack buildTrack(JsonBrowser trackJson, JsonBrowser included, TidalAudioSourceManager sourceManager) {
        JsonBrowser trackAttributes = trackJson.get("attributes");

        AudioTrackInfoBuilder builder = AudioTrackInfoBuilder.empty();

        builder
                .setTitle(trackAttributes.get("title").text())
                .setIsrc(trackAttributes.get("isrc").text())
                .setExplicit(trackAttributes.get("explicit").asBoolean(false))
                .setLength(durationToMillis(trackAttributes.get("duration").text()))
                .setIdentifier(trackJson.get("id").text())
                .setUri(TidalConstants.TRACK_URL + trackJson.get("id").text());

        for (JsonBrowser artistRelationship : trackJson.get("relationships").get("artists").get("data").values()) {
            String artistId = artistRelationship.get("id").text();
            if (!SourceTools.isBlank(artistId)) {
                JsonBrowser artistJson = findRelationship(included, artistId, "artists");
                if (artistJson != null && !artistJson.isNull()) {
                    builder.addArtist(new AudioTrackAuthorInfo(artistJson.get("attributes").get("name").text(), TidalConstants.ARTIST_URL + artistId));
                }
            }
        }

        String albumId = trackJson.get("relationships").get("albums").get("data").index(0).get("id").text();
        if (!SourceTools.isBlank(albumId)) {
            JsonBrowser albumJson = findRelationship(included, albumId, "albums");
            if (albumJson != null) {
                String coverId = albumJson.get("relationships").get("coverArt").get("data").index(0).get("id").text();
                if (!SourceTools.isBlank(coverId)) {
                    JsonBrowser artworkJson = findRelationship(included, coverId, "artworks");
                    if (artworkJson != null && !artworkJson.get("attributes").isNull()) {
                        builder.setArtworkUrl(findBestArtwork(artworkJson));
                    }
                }
            }
        }

        return new ThirdPartyAudioTrack(builder.build(), sourceManager);
    }

    public static URI trackUri(String id, String countryCode) {
        try {
            return buildUri(TidalConstants.TRACK_API_URL)
                    .addParameter("filter[id]", id)
                    .addParameter("countryCode", countryCode)
                    .addParameter("locale", TidalConstants.DEFAULT_LOCALE)
                    .addParameter("include", "albums,albums.coverArt,artists")
                    .build();
        } catch (URISyntaxException e)  {
            return null;
        }
    }

    public static URI albumUri(String id, String countryCode) {
        try {
            return buildUri(TidalConstants.ALBUM_API_URL)
                    .addParameter("filter[id]", id)
                    .addParameter("countryCode", countryCode)
                    .addParameter("locale", TidalConstants.DEFAULT_LOCALE)
                    .addParameter("include", "artists,items,coverArt,items.albums,items.artists,items.albums.coverArt")
                    .build();
        } catch (URISyntaxException e)  {
            return null;
        }
    }

    public static URI playlistUri(String id, String countryCode) {
        try {
            return buildUri(TidalConstants.PLAYLIST_API_URL)
                    .addParameter("filter[id]", id)
                    .addParameter("countryCode", countryCode)
                    .addParameter("locale", TidalConstants.DEFAULT_LOCALE)
                    .addParameter("include", "coverArt,collaboratorProfiles,ownerProfiles,items,items.artists,items.albums,items.albums.coverArt")
                    .build();
        } catch (URISyntaxException e)  {
            return null;
        }
    }

    public static URI searchTracksUri(String query, String countryCode) {
        try {
            return buildUri(String.format(TidalConstants.SEARCH_API_URL, encodePathSegment(query)))
                    .addParameter("countryCode", countryCode)
                    .addParameter("locale", TidalConstants.DEFAULT_LOCALE)
                    .addParameter("include", "tracks,tracks.albums,tracks.albums.coverArt,tracks.artists")
                    .build();
        } catch (URISyntaxException e)  {
            return null;
        }
    }

    public static URI artistTracksUri(String artistId, String countryCode) {
        try {
            return buildUri(TidalConstants.ARTIST_API_TRACKS)
                    .addParameter("filter[id]", artistId)
                    .addParameter("countryCode", countryCode)
                    .addParameter("locale", TidalConstants.DEFAULT_LOCALE)
                    .addParameter("include", "profileArt,albums,albums.coverArt,albums.items,albums.items.albums")
                    .build();
        } catch (URISyntaxException e)  {
            return null;
        }
    }

    private static SafeUriBuilder buildUri(String base) {
        return new SafeUriBuilder(base);
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    public static long durationToMillis(String duration) {
        if (duration == null || duration.isBlank()) {
            return Units.DURATION_MS_UNKNOWN;
        }

        try {
            return Duration.parse(duration).toMillis();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid ISO-8601 duration: " + duration, e);
        }
    }

    private static class SafeUriBuilder extends URIBuilder {
        private SafeUriBuilder(String string) {
            super(URI.create(string));
        }

        @Override
        public URI build() {
            try {
                return super.build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
