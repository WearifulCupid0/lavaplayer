package com.sedmelluq.lavaplayer.extensions.cache;

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.time.Duration;

public interface CacheableAudioSourceManager extends AudioSourceManager {
    Boolean shouldCacheTrack(AudioTrack track);

    Duration getTrackTtl(AudioTrack track);
}
