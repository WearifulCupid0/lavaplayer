package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;

public interface MixcloudApiLoader {
  ExtendedHttpConfigurable getHttpConfiguration();
  void shutdown();
}