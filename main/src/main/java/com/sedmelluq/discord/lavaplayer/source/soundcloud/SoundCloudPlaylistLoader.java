package com.sedmelluq.discord.lavaplayer.source.soundcloud;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.util.function.Function;

public interface SoundCloudPlaylistLoader {
  AudioPlaylist load(
      String identifier,
      HttpInterfaceManager httpInterfaceManager,
      Function<JsonBrowser, AudioTrack> trackFactory
  );
}
