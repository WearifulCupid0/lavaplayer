package com.sedmelluq.discord.lavaplayer.source;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.bilibili.BilibiliAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.clyp.ClypAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.getyarn.GetyarnAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.iheart.iHeartAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.instagram.InstagramAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.jamendo.JamendoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.newgrounds.NewgroundsAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.odysee.OdyseeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.reddit.RedditAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.tiktok.TiktokAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.yamusic.YandexMusicAudioSourceManager;
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
    playerManager.registerSourceManager(new YandexMusicAudioSourceManager(true));
    playerManager.registerSourceManager(new JamendoAudioSourceManager(true));
    playerManager.registerSourceManager(new MixcloudAudioSourceManager(true));
    playerManager.registerSourceManager(new OdyseeAudioSourceManager(true));
    playerManager.registerSourceManager(new BilibiliAudioSourceManager(true));
    playerManager.registerSourceManager(new iHeartAudioSourceManager(true, containerRegistry));
    playerManager.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
    playerManager.registerSourceManager(new BandcampAudioSourceManager());
    playerManager.registerSourceManager(new RedditAudioSourceManager());
    playerManager.registerSourceManager(new InstagramAudioSourceManager());
    playerManager.registerSourceManager(new TiktokAudioSourceManager());
    playerManager.registerSourceManager(new VimeoAudioSourceManager());
    playerManager.registerSourceManager(new TwitchStreamAudioSourceManager());
    playerManager.registerSourceManager(new ClypAudioSourceManager());
    playerManager.registerSourceManager(new BandlabAudioSourceManager());
    playerManager.registerSourceManager(new NewgroundsAudioSourceManager());
    playerManager.registerSourceManager(new GetyarnAudioSourceManager());
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
