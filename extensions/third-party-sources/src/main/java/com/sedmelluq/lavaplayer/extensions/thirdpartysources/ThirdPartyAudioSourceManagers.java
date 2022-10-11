package com.sedmelluq.lavaplayer.extensions.thirdpartysources;

import com.sedmelluq.lavaplayer.extensions.thirdpartysources.applemusic.AppleMusicAudioSourceManager;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.napster.NapsterAudioSourceManager;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.spotify.SpotifyAudioSourceManager;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.tidal.TidalAudioSourceManager;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.yamusic.YandexMusicAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

/**
 * A helper class for registering built-in third party source managers to a player manager.
 */
public class ThirdPartyAudioSourceManagers {

  /**
   * Registers all built-in third party audio sources to the specified player manager.
   *
   * @param playerManager Player manager to register the source managers to
   */
  public static void registerThirdPartySources(AudioPlayerManager playerManager) {
    playerManager.registerSourceManager(new AppleMusicAudioSourceManager(playerManager));
    playerManager.registerSourceManager(new NapsterAudioSourceManager(playerManager));
    playerManager.registerSourceManager(new SpotifyAudioSourceManager(playerManager));
    playerManager.registerSourceManager(new TidalAudioSourceManager(playerManager));
    playerManager.registerSourceManager(new YandexMusicAudioSourceManager(playerManager));
  }
}
