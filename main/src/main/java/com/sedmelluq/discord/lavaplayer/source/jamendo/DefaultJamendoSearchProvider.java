package com.sedmelluq.discord.lavaplayer.source.jamendo;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultJamendoSearchProvider extends AbstractJamendoApiLoader implements JamendoSearchResultLoader {
    private static final String TRACKS_INFO_FORMAT = "https://api.jamendo.com/v3.0/tracks?search=%s";

    private static final String SEARCH_PREFIX = "jmsearch";

    private static final Pattern SEARCH_PATTERN = Pattern.compile("jmsearch:([^:]+)");

    @Override
    public AudioItem loadSearchResult(String query, String clientId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        if (query == null || !query.startsWith(SEARCH_PREFIX)) return null;
        Matcher matcher = SEARCH_PATTERN.matcher(query);
        if (!matcher.find()) return null;
        String text = matcher.group(1);
        try {
            return extractFromApi(String.format(TRACKS_INFO_FORMAT, URLEncoder.encode(text, "UTF-8")), clientId, (httpClient, results) -> {
                if(results.index(0).isNull() || !results.isList()) return null;

                ArrayList<AudioTrack> tracks = new ArrayList<>();
                results.values()
                .forEach(track -> {
                    String artwork = track.get("image").text().replace("width=200", "width=500");
                    String artist = track.get("artist_name").text();
                    tracks.add(JamendoUtils.extractTrack(track, artist, artwork, trackFactory));
                });

                return new BasicAudioPlaylist("Search results for: " + text, "search", tracks, null, true);
            });
        } catch (Exception e) {
            throw new FriendlyException("Could not load search results", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }
}
