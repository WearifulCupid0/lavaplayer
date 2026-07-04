package com.sedmelluq.lavaplayer.extensions.thirdpartysources;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.deezer.DeezerAudioSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ThirdPartyAudioTrack extends DelegatedAudioTrack {
	private static final Logger log = LoggerFactory.getLogger(ThirdPartyAudioTrack.class);
    
    private static final String ISRC_PATTERN = "%ISRC%";
	private static final String QUERY_PATTERN = "%QUERY%";

    private final String[] providers = {
		"dzisrc:" + ISRC_PATTERN,
		"ytsearch:\"" + ISRC_PATTERN + "\"",
		"ytmsearch:" + QUERY_PATTERN,
		"ytsearch:" + QUERY_PATTERN
	};

	protected final ThirdPartyAudioSourceManager sourceManager;

	public ThirdPartyAudioTrack(AudioTrackInfo trackInfo, ThirdPartyAudioSourceManager sourceManager) {
		super(trackInfo);
		this.sourceManager = sourceManager;
	}

	public String getISRC() {
		return this.trackInfo.isrc;
	}

	private String getTrackTitle() {
		String query = this.trackInfo.title;
		if(!this.trackInfo.author.startsWith("Unknown")) {
			query += " - " + this.trackInfo.author;
		}
		return query;
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		AudioItem track = null;

		for(String provider : this.providers) {
			if(provider.contains(ISRC_PATTERN)) {
				String isrc = this.getISRC();
				if(isrc != null) {
					provider = provider.replace(ISRC_PATTERN, isrc.replaceAll("-", ""));
				} else {
                    if(this.sourceManager.isFetchIsrcEnabled()) {
                        isrc = this.sourceManager.fetchIsrc(this);
                        if(isrc != null) {
                            provider = provider.replace(ISRC_PATTERN, isrc.replaceAll("-", ""));
                        }
                    }

					log.debug("Ignoring identifier \"" + provider + "\" because this track does not have an ISRC");
					continue;
				}
			}

			provider = provider.replace(QUERY_PATTERN, getTrackTitle());
			AudioItem result = loadItem(provider);
			if (result != null) {
				if (result instanceof ThirdPartyAudioTrack) {
					AudioSourceManager audioSourceManager = ((ThirdPartyAudioTrack) result).getSourceManager();
					if (!(audioSourceManager instanceof DeezerAudioSourceManager) || !((DeezerAudioSourceManager) audioSourceManager).canPlayNative())
						throw new RuntimeException("A third party audio track can be native played.");
				}
				if (result instanceof InternalAudioTrack) {
					track = result;
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
							if (info.title.toLowerCase().contains(this.trackInfo.title.toLowerCase()) &&
								info.author.toLowerCase().contains(this.trackInfo.author.toLowerCase())) {
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

		if (track != null) {
			processDelegate((InternalAudioTrack) track, executor);
			return;
		}

		throw new FriendlyException("No matching track found", FriendlyException.Severity.COMMON, null);
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return this.sourceManager;
	}

	public AudioItem loadItem(String query) {
		CompletableFuture<AudioItem> future = new CompletableFuture<AudioItem>();
		this.sourceManager.getAudioPlayerManager().loadItem(query, new AudioLoadResultHandler() {
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
	protected AudioTrack makeShallowClone() {
		return new ThirdPartyAudioTrack(this.trackInfo, this.sourceManager);
	}
}