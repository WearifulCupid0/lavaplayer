package com.sedmelluq.lavaplayer.extensions.thirdpartysources;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Function;

public abstract class ThirdPartyAudioSourceManager implements AudioSourceManager {
	public static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.0.0 Safari/537.36";
    private final AudioPlayerManager playerManager;

	protected ThirdPartyAudioSourceManager(AudioPlayerManager playerManager) {
		this.playerManager = playerManager;
	}

    public AudioPlayerManager getAudioPlayerManager() {
        return this.playerManager;
    }

	@Override
	public boolean isTrackEncodable(AudioTrack track){
		return true;
	}

	@Override
	public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {

	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException{
		return new ThirdPartyAudioTrack(trackInfo, this);
	}
}
