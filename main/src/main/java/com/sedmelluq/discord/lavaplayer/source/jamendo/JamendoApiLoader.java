package com.sedmelluq.discord.lavaplayer.source.jamendo;

import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;

public interface JamendoApiLoader {
  ExtendedHttpConfigurable getHttpConfiguration();
  void shutdown();
}