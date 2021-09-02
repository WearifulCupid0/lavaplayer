package com.sedmelluq.discord.lavaplayer.source.youtube;

public interface YoutubeLinkRouter {
  <T> T route(String link, Routes<T> routes);

  interface Routes<T> {
    T track(String videoId);

    T track(String videoId, Long time);

    T channel(String channelId);

    T playlist(String playlistId, String selectedVideoId);

    T mix(String mixId, String selectedVideoId);

    T search(String query);

    T searchMusic(String query);

    T similar(String videoId);

    T anonymous(String videoIds);

    T browse(String browseId);

    T none();
  }
}
