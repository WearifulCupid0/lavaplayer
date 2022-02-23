package com.sedmelluq.discord.lavaplayer.source.youtube;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import java.util.function.Function;

public interface YoutubeSimilarTrackLoader {
  AudioItem load(
      HttpInterface httpInterface,
      String videoId,
      Function<AudioTrackInfo, AudioTrack> trackFactory
  );
}
