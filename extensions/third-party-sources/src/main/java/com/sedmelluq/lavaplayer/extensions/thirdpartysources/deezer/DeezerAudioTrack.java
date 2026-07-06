package com.sedmelluq.lavaplayer.extensions.thirdpartysources.deezer;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.ThirdPartyAudioTrack;
import org.apache.commons.codec.binary.Hex;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DeezerAudioTrack extends DelegatedAudioTrack {
    private final DeezerAudioSourceManager sourceManager;
    public DeezerAudioTrack(AudioTrackInfo trackInfo, DeezerAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = this.sourceManager.getHttpInterface(true)) {
            if (this.sourceManager.canPlayNative()) {
                this.sourceManager.setupDeezerHttpInterface(httpInterface);

                DeezerAudioSourceManager.DeezerMediaSource source =
                        this.sourceManager.getMediaSource(httpInterface, this.trackInfo.identifier);

                try (DeezerPersistentHttpStream stream = new DeezerPersistentHttpStream(
                        httpInterface,
                        source.getUrl(),
                        source.getContentLength(),
                        this.getTrackDecryptionKey(source.getTrackId())
                )) {
                    processDelegate(new Mp3AudioTrack(this.trackInfo, stream), executor);
                }

                return;
            }

            processDelegate(new ThirdPartyAudioTrack(trackInfo, this.sourceManager), executor);
        }
    }

    private byte[] getTrackDecryptionKey(String trackId) throws NoSuchAlgorithmException {
        char[] md5 = Hex.encodeHex(
                MessageDigest.getInstance("MD5").digest(trackId.getBytes()),
                true
        );

        byte[] key = new byte[16];

        for (int i = 0; i < 16; i++) {
            key[i] = (byte) (md5[i] ^ md5[i + 16] ^ this.sourceManager.masterKey.charAt(i));
        }

        return key;
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new DeezerAudioTrack(this.trackInfo, this.sourceManager);
    }

    @Override
    public DeezerAudioSourceManager getSourceManager() {
        return this.sourceManager;
    }
}
