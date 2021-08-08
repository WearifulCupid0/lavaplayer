package com.sedmelluq.discord.lavaplayer.source.saavn;

import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;

public interface SaavnApiLoader {
    ExtendedHttpConfigurable getHttpConfiguration();
    void shutdown();
}