package com.sedmelluq.lavaplayer.extensions.cache;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageInput;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageOutput;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.DecodedTrackHolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class CachedLoadResult {
    public String type; // track, playlist, no_matches

    public String encodedTrack;

    public CachedPlaylist playlist;

    public long createdAt;

    public static CachedLoadResult track(AudioPlayerManager manager, AudioTrack track) throws IOException {
        CachedLoadResult result = new CachedLoadResult();
        result.type = "track";
        result.encodedTrack = encodeTrack(manager, track);
        result.createdAt = System.currentTimeMillis();
        return result;
    }

    public static CachedLoadResult playlist(AudioPlayerManager manager, AudioPlaylist playlist) throws IOException {
        CachedLoadResult result = new CachedLoadResult();
        result.type = "playlist";
        result.createdAt = System.currentTimeMillis();

        CachedPlaylist cachedPlaylist = new CachedPlaylist();
        cachedPlaylist.name = playlist.getName();
        cachedPlaylist.creator = playlist.getCreator();
        cachedPlaylist.image = playlist.getImage();
        cachedPlaylist.uri = playlist.getURI();
        cachedPlaylist.type = playlist.getType();
        cachedPlaylist.searchResult = playlist.isSearchResult();
        cachedPlaylist.tracks = new ArrayList<>();
        cachedPlaylist.selectedTrackIndex = -1;

        AudioTrack selectedTrack = playlist.getSelectedTrack();

        for (int i = 0; i < playlist.getTracks().size(); i++) {
            AudioTrack track = playlist.getTracks().get(i);
            cachedPlaylist.tracks.add(encodeTrack(manager, track));

            if (selectedTrack != null && sameTrack(selectedTrack, track)) {
                cachedPlaylist.selectedTrackIndex = i;
            }
        }

        result.playlist = cachedPlaylist;
        return result;
    }

    public static CachedLoadResult noMatches() {
        CachedLoadResult result = new CachedLoadResult();
        result.type = "no_matches";
        result.createdAt = System.currentTimeMillis();
        return result;
    }

    public boolean dispatch(AudioPlayerManager manager, AudioLoadResultHandler handler) {
        try {
            if ("track".equals(type)) {
                AudioTrack track = decodeTrack(manager, encodedTrack);

                if (track == null) {
                    return false;
                }

                handler.trackLoaded(track);
                return true;
            }

            if ("playlist".equals(type)) {
                AudioPlaylist decodedPlaylist = decodePlaylist(manager);

                if (decodedPlaylist == null) {
                    return false;
                }

                handler.playlistLoaded(decodedPlaylist);
                return true;
            }

            if ("no_matches".equals(type)) {
                handler.noMatches();
                return true;
            }

            return false;
        } catch (Exception e) {
            handler.loadFailed(new FriendlyException(
                    "Failed to read cached load result.",
                    FriendlyException.Severity.SUSPICIOUS,
                    e
            ));
            return true;
        }
    }

    @JsonIgnore
    private AudioPlaylist decodePlaylist(AudioPlayerManager manager) throws IOException {
        if (playlist == null || playlist.tracks == null) {
            return null;
        }

        List<AudioTrack> decodedTracks = new ArrayList<>();

        for (String encoded : playlist.tracks) {
            AudioTrack track = decodeTrack(manager, encoded);

            if (track != null) {
                decodedTracks.add(track);
            }
        }

        if (decodedTracks.isEmpty()) {
            return null;
        }

        AudioTrack selectedTrack = null;

        if (playlist.selectedTrackIndex >= 0 && playlist.selectedTrackIndex < decodedTracks.size()) {
            selectedTrack = decodedTracks.get(playlist.selectedTrackIndex);
        }

        return new BasicAudioPlaylist(
                playlist.name,
                playlist.creator,
                playlist.image,
                playlist.uri,
                playlist.type,
                decodedTracks,
                selectedTrack,
                playlist.searchResult
        );
    }

    private static String encodeTrack(AudioPlayerManager manager, AudioTrack track) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MessageOutput messageOutput = new MessageOutput(outputStream);

        manager.encodeTrack(messageOutput, track);

        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    private static AudioTrack decodeTrack(AudioPlayerManager manager, String encodedTrack) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(encodedTrack);
        MessageInput messageInput = new MessageInput(new ByteArrayInputStream(bytes));

        DecodedTrackHolder holder = manager.decodeTrack(messageInput);

        return holder != null ? holder.decodedTrack : null;
    }

    private static boolean sameTrack(AudioTrack first, AudioTrack second) {
        if (first == second) {
            return true;
        }

        if (first == null || second == null) {
            return false;
        }

        return first.getInfo().identifier.equals(second.getInfo().identifier)
                && first.getSourceManager().getSourceName().equals(second.getSourceManager().getSourceName());
    }

    public static class CachedPlaylist {
        public String name;
        public String creator;
        public String image;
        public String uri;
        public String type;
        public boolean searchResult;
        public List<String> tracks;
        public int selectedTrackIndex;
    }
}