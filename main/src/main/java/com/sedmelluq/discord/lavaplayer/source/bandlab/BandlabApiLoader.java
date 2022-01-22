package com.sedmelluq.discord.lavaplayer.source.bandlab;

import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;

public interface BandlabApiLoader {
    ExtendedHttpConfigurable getHttpConfiguration();
    void shutdown();
}