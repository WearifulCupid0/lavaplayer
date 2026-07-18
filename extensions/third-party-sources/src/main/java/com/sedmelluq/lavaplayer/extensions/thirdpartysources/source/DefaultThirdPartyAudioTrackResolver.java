package com.sedmelluq.lavaplayer.extensions.thirdpartysources.source;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.deezer.DeezerAudioSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.sedmelluq.lavaplayer.extensions.thirdpartysources.source.ThirdPartyAudioTrack.ISRC_PATTERN;
import static com.sedmelluq.lavaplayer.extensions.thirdpartysources.source.ThirdPartyAudioTrack.QUERY_PATTERN;

public class DefaultThirdPartyAudioTrackResolver implements ThirdPartyAudioTrackResolver {
    private static final Logger log = LoggerFactory.getLogger(DefaultThirdPartyAudioTrackResolver.class);

    private final String[] providers = {
            "dzisrc:" + ISRC_PATTERN,
            "ytsearch:\"" + ISRC_PATTERN + "\"",
            "ytmsearch:" + QUERY_PATTERN,
            "ytsearch:" + QUERY_PATTERN
    };

    private String getTrackTitle(AudioTrackInfo trackInfo) {
        String query = trackInfo.title;
        if(!trackInfo.artists.isEmpty()) {
            query += " - " + trackInfo.artists.get(0).name;
        }
        return query;
    }

    private AudioItem loadItem(String query, AudioPlayerManager playerManager) {
        CompletableFuture<AudioItem> future = new CompletableFuture<AudioItem>();
        playerManager.loadItem(query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                future.complete(track);
            }
            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                future.complete(playlist);
            }
            @Override
            public void noMatches(){
                future.complete(null);
            }
            @Override
            public void loadFailed(FriendlyException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future.join();
    }

    @Override
    public AudioTrack resolve(ThirdPartyAudioTrack thirdPartyTrack, AudioPlayerManager playerManager) {
        AudioTrack track = null;

        for(String provider : this.providers) {
            if(provider.contains(ISRC_PATTERN)) {
                String isrc = thirdPartyTrack.getIsrc();
                if(isrc != null) {
                    provider = provider.replace(ISRC_PATTERN, isrc.replaceAll("-", ""));
                } else {
                    log.debug("Ignoring identifier \"" + provider + "\" because this track does not have an ISRC");
                    continue;
                }
            }

            provider = provider.replace(QUERY_PATTERN, getTrackTitle(thirdPartyTrack.getInfo()));
            AudioItem result = loadItem(provider, playerManager);
            if (result != null) {
                if (result instanceof InternalAudioTrack) {
                    track = (InternalAudioTrack) result;
                    break;
                }
                if (result instanceof AudioPlaylist) {
                    List<AudioTrack> tracks = ((AudioPlaylist) result).getTracks();
                    if (tracks.size() <= 0) {
                        continue;
                    }
                    if (provider.startsWith("ytm")) {
                        for (AudioTrack t : tracks) {
                            AudioTrackInfo info = t.getInfo();
                            if (info.title.toLowerCase().contains(thirdPartyTrack.getInfo().title.toLowerCase()) &&
                                    info.artists.get(0).name.toLowerCase().contains(thirdPartyTrack.getInfo().artists.get(0).name.toLowerCase())) {
                                track = t;
                                break;
                            }
                        }

                        if (track != null) {
                            break;
                        } else {
                            continue;
                        }
                    }
                    track = tracks.get(0);
                    break;
                }
            }
        }

        return track;
    }
}
