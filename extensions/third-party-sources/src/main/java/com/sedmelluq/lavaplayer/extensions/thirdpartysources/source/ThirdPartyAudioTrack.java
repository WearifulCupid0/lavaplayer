package com.sedmelluq.lavaplayer.extensions.thirdpartysources.source;

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
    
    public static final String ISRC_PATTERN = "%ISRC%";
	public static final String QUERY_PATTERN = "%QUERY%";

    private final String[] providers = {
		"dzisrc:" + ISRC_PATTERN,
		"ytsearch:\"" + ISRC_PATTERN + "\"",
		"ytmsearch:" + QUERY_PATTERN,
		"ytsearch:" + QUERY_PATTERN
	};

	protected final ThirdPartyAudioSourceManager sourceManager;
	protected final ThirdPartyAudioTrackResolver trackResolver;

	public ThirdPartyAudioTrack(AudioTrackInfo trackInfo, ThirdPartyAudioSourceManager sourceManager) {
		this(trackInfo, sourceManager.getTrackResolver(), sourceManager);
	}

	public ThirdPartyAudioTrack(AudioTrackInfo trackInfo, ThirdPartyAudioTrackResolver trackResolver, ThirdPartyAudioSourceManager sourceManager) {
		super(trackInfo);

		this.trackResolver = trackResolver;
		this.sourceManager = sourceManager;
	}

	public String getIsrc() {
		return this.trackInfo.isrc;
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		AudioTrack track = trackResolver.resolve(this, sourceManager.getAudioPlayerManager());

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

	@Override
	protected AudioTrack makeShallowClone() {
		return new ThirdPartyAudioTrack(this.trackInfo, this.sourceManager);
	}
}