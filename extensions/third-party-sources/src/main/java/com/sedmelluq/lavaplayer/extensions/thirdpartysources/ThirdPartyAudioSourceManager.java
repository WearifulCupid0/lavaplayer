package com.sedmelluq.lavaplayer.extensions.thirdpartysources;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public abstract class ThirdPartyAudioSourceManager implements AudioSourceManager {
    private final AudioPlayerManager playerManager;
    private final boolean fetchIsrc;

	protected ThirdPartyAudioSourceManager(AudioPlayerManager playerManager, boolean fetchIsrc) {
        this.fetchIsrc = fetchIsrc;
		this.playerManager = playerManager;
	}

    public AudioPlayerManager getAudioPlayerManager() {
        return this.playerManager;
    }

    public boolean isFetchIsrcEnabled() {
        return this.fetchIsrc;
    }

    public String fetchIsrc(AudioTrack identifier) {
        return null;
    }

	@Override
	public boolean isTrackEncodable(AudioTrack track){
		return true;
	}

	@Override
	public void encodeTrack(AudioTrack track, DataOutput output) throws IOException{
		ThirdPartyAudioTrack audioTrack = ((ThirdPartyAudioTrack) track);
		DataFormatTools.writeNullableText(output, audioTrack.getISRC());
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException{
		return new ThirdPartyAudioTrack(trackInfo, DataFormatTools.readNullableText(input), this);
	}
}
