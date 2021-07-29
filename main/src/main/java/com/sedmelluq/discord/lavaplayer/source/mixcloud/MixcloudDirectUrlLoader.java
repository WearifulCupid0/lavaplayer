package com.sedmelluq.discord.lavaplayer.source.mixcloud;

public interface MixcloudDirectUrlLoader extends MixcloudApiLoader {
    String getStreamUrl(String url);
}
