package com.sedmelluq.lavaplayer.extensions.thirdpartysources.source;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public interface ThirdPartyAudioTrackResolver {
    AudioTrack resolve(ThirdPartyAudioTrack track, AudioPlayerManager playerManager);
}
