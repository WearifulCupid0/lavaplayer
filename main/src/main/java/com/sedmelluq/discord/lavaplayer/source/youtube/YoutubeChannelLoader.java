package com.sedmelluq.discord.lavaplayer.source.youtube;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import java.util.function.Function;

public interface YoutubeChannelLoader {
    AudioPlaylist load(String channelId, HttpInterface httpInterface, Function<AudioTrackInfo, AudioTrack> trackFactory);
}