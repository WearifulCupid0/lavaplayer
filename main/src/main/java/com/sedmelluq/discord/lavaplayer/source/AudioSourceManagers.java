package com.sedmelluq.discord.lavaplayer.source;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.bilibili.BilibiliAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.clyp.ClypAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.deezer.DeezerAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.getyarn.GetyarnAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.google.tts.GoogleTTSAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.iheart.iHeartAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.jamendo.JamendoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.jiosaavn.JioSaavnAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.ocremix.OcremixAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.odysee.OdyseeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.reddit.RedditAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.reverbnation.ReverbnationAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.rumble.RumbleAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundgasm.SoundgasmAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.streamable.StreamableAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.tiktok.TiktokAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.tunein.TuneinAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitter.TwitterAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;

/**
 * A helper class for registering built-in source managers to a player manager.
 */
public class AudioSourceManagers {
  /**
   * See {@link #registerRemoteSources(AudioPlayerManager, MediaContainerRegistry)}, but with default containers.
   */
  public static void registerRemoteSources(AudioPlayerManager playerManager) {
    registerRemoteSources(playerManager, MediaContainerRegistry.DEFAULT_REGISTRY);
  }

  /**
   * Registers all built-in remote audio sources to the specified player manager. Local file audio source must be
   * registered separately.
   *
   * @param playerManager Player manager to register the source managers to
   * @param containerRegistry Media container registry to be used by any probing sources.
   */
  public static void registerRemoteSources(AudioPlayerManager playerManager, MediaContainerRegistry containerRegistry) {
    playerManager.registerSourceManager(new YoutubeAudioSourceManager(true, null, null));
    playerManager.registerSourceManager(new JamendoAudioSourceManager(true));
    playerManager.registerSourceManager(new MixcloudAudioSourceManager(true));
    playerManager.registerSourceManager(new OdyseeAudioSourceManager(true));
    playerManager.registerSourceManager(new BilibiliAudioSourceManager(true));
    playerManager.registerSourceManager(new VimeoAudioSourceManager(true));
    playerManager.registerSourceManager(new iHeartAudioSourceManager(true, containerRegistry));
    playerManager.registerSourceManager(new JioSaavnAudioSourceManager(true));
    playerManager.registerSourceManager(new DeezerAudioSourceManager(null,true));
    playerManager.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
    playerManager.registerSourceManager(new GoogleTTSAudioSourceManager());
    playerManager.registerSourceManager(new SoundgasmAudioSourceManager());
    playerManager.registerSourceManager(new BandcampAudioSourceManager());
    playerManager.registerSourceManager(new StreamableAudioSourceManager());
    playerManager.registerSourceManager(new OcremixAudioSourceManager());
    playerManager.registerSourceManager(new TuneinAudioSourceManager());
    playerManager.registerSourceManager(new RedditAudioSourceManager());
    playerManager.registerSourceManager(new TwitterAudioSourceManager());
    playerManager.registerSourceManager(new TiktokAudioSourceManager());
    playerManager.registerSourceManager(new TwitchStreamAudioSourceManager());
    playerManager.registerSourceManager(new ClypAudioSourceManager());
    playerManager.registerSourceManager(new ReverbnationAudioSourceManager());
    playerManager.registerSourceManager(new BandlabAudioSourceManager());
    playerManager.registerSourceManager(new GetyarnAudioSourceManager());
    playerManager.registerSourceManager(new RumbleAudioSourceManager());
    playerManager.registerSourceManager(new HttpAudioSourceManager(containerRegistry));
  }

  /**
   * Registers the local file source manager to the specified player manager.
   *
   * @param playerManager Player manager to register the source manager to
   */
  public static void registerLocalSource(AudioPlayerManager playerManager) {
    registerLocalSource(playerManager, MediaContainerRegistry.DEFAULT_REGISTRY);
  }

  /**
   * Registers the local file source manager to the specified player manager.
   *
   * @param playerManager Player manager to register the source manager to
   * @param containerRegistry Media container registry to be used by the local source.
   */
  public static void registerLocalSource(AudioPlayerManager playerManager, MediaContainerRegistry containerRegistry) {
    playerManager.registerSourceManager(new LocalAudioSourceManager(containerRegistry));
  }
}
