package com.sedmelluq.lavaplayer.extensions.thirdpartysources.yamusic;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.function.Function;

public interface YandexMusicTrackLoader extends YandexMusicApiLoader {
  AudioItem loadTrack(String albumId, String trackId, Function<AudioTrackInfo, AudioTrack> trackFactory);
}
