package com.sedmelluq.lavaplayer.extensions.cache;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public interface AudioLoadCache extends AutoCloseable {
    CachedLoadResult get(AudioPlayerManager manager, AudioReference reference) throws Exception;

    void putTrack(AudioPlayerManager manager, AudioReference reference, AudioTrack track) throws Exception;

    void putPlaylist(AudioPlayerManager manager, AudioReference reference, AudioPlaylist playlist) throws Exception;

    void putNoMatches(AudioReference reference) throws Exception;

    void invalidate(AudioReference reference) throws Exception;

    @Override
    void close();
}
