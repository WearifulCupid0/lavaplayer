package com.sedmelluq.lavaplayer.extensions.thirdpartysources.yamusic;

import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;

public interface YandexMusicApiLoader {
  ExtendedHttpConfigurable getHttpConfiguration();
  void shutdown();
}
