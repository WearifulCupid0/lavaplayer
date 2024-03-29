package com.sedmelluq.discord.lavaplayer.source.soundcloud;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;

public interface SoundCloudDataLoader {
  JsonBrowser load(HttpInterface httpInterface, String url) throws Exception;
}
