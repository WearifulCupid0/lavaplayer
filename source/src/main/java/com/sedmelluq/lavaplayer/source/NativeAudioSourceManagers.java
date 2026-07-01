package com.sedmelluq.lavaplayer.source;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.lavaplayer.source.bandlab.BandlabAudioSourceManager;
import com.sedmelluq.lavaplayer.source.bilibili.BilibiliAudioSourceManager;
import com.sedmelluq.lavaplayer.source.clyp.ClypAudioSourceManager;
import com.sedmelluq.lavaplayer.source.iheart.iHeartAudioSourceManager;
import com.sedmelluq.lavaplayer.source.jamendo.JamendoAudioSourceManager;
import com.sedmelluq.lavaplayer.source.jiosaavn.JioSaavnAudioSourceManager;
import com.sedmelluq.lavaplayer.source.mixcloud.MixcloudAudioSourceManager;
import com.sedmelluq.lavaplayer.source.ocremix.OcremixAudioSourceManager;
import com.sedmelluq.lavaplayer.source.odysee.OdyseeAudioSourceManager;
import com.sedmelluq.lavaplayer.source.reverbnation.ReverbnationAudioSourceManager;
import com.sedmelluq.lavaplayer.source.rumble.RumbleAudioSourceManager;
import com.sedmelluq.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.lavaplayer.source.soundgasm.SoundgasmAudioSourceManager;
import com.sedmelluq.lavaplayer.source.streamable.StreamableAudioSourceManager;
import com.sedmelluq.lavaplayer.source.tunein.TuneinAudioSourceManager;
import com.sedmelluq.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.lavaplayer.source.vimeo.VimeoAudioSourceManager;

/**
 * A helper class for registering built-in source managers to a player manager.
 */
public class NativeAudioSourceManagers {
  /**
   * See {@link #registerNativeSources(AudioPlayerManager, MediaContainerRegistry)}, but with default containers.
   */
  public static void registerNativeSources(AudioPlayerManager playerManager) {
    registerNativeSources(playerManager, MediaContainerRegistry.DEFAULT_REGISTRY);
  }

  /**
   * Registers all built-in remote audio sources to the specified player manager. Local file audio source must be
   * registered separately.
   *
   * @param playerManager Player manager to register the source managers to
   * @param containerRegistry Media container registry to be used by any probing sources.
   */
  public static void registerNativeSources(AudioPlayerManager playerManager, MediaContainerRegistry containerRegistry) {
    playerManager.registerSourceManager(new JamendoAudioSourceManager(true));
    playerManager.registerSourceManager(new MixcloudAudioSourceManager(true));
    playerManager.registerSourceManager(new OdyseeAudioSourceManager(true));
    playerManager.registerSourceManager(new BilibiliAudioSourceManager(true));
    playerManager.registerSourceManager(new VimeoAudioSourceManager());
    playerManager.registerSourceManager(new iHeartAudioSourceManager(true, containerRegistry));
    playerManager.registerSourceManager(new JioSaavnAudioSourceManager(true));
    playerManager.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
    playerManager.registerSourceManager(new SoundgasmAudioSourceManager());
    playerManager.registerSourceManager(new BandcampAudioSourceManager());
    playerManager.registerSourceManager(new StreamableAudioSourceManager());
    playerManager.registerSourceManager(new OcremixAudioSourceManager());
    playerManager.registerSourceManager(new TuneinAudioSourceManager());
    playerManager.registerSourceManager(new TwitchStreamAudioSourceManager());
    playerManager.registerSourceManager(new ClypAudioSourceManager());
    playerManager.registerSourceManager(new ReverbnationAudioSourceManager());
    playerManager.registerSourceManager(new BandlabAudioSourceManager());
    playerManager.registerSourceManager(new RumbleAudioSourceManager());
  }
}
