package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public interface MixcloudGraphqlHandler {
    AudioTrack processAsSigleTrack(String slug, String username);
    AudioPlaylist processPlaylist(String slug, String username);
    AudioPlaylist processSearch(String query);
}