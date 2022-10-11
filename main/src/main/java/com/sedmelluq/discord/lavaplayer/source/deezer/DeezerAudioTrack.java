package com.sedmelluq.discord.lavaplayer.source.deezer;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

import java.net.URI;
import java.nio.charset.StandardCharsets;
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
        try (HttpInterface httpInterface = this.sourceManager.getHttpInterface()) {
            try (DeezerPersistentHttpStream stream = new DeezerPersistentHttpStream(httpInterface, this.getTrackMediaURI(), this.trackInfo.length, this.getTrackDecryptionKey())) {
                processDelegate(new Mp3AudioTrack(this.trackInfo, stream), executor);
            }
        }
    }

    private String getToken() throws Exception {
        HttpPost postSongData = new HttpPost(DeezerConstants.AJAX_URL + "?method=song.getData");
        postSongData.setEntity(new StringEntity("{\"sng_id\":\"" + this.trackInfo.identifier + "\"}", ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse response = this.sourceManager.getHttpInterface().execute(postSongData)) {
            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            return json.get("results").get("TRACK_TOKEN").text();
        }
    }

    private URI getTrackMediaURI() throws Exception {
        HttpPost postMediaURL = new HttpPost(DeezerConstants.MEDIA_URL);
        String token = this.getToken();
        if (token == null) throw  new Exception("Song unavailable");
        postMediaURL.setEntity(new StringEntity(String.format(DeezerConstants.MEDIA_PAYLOAD, this.sourceManager.getLicenseToken(), token), ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse response = this.sourceManager.getHttpInterface().execute(postMediaURL)) {
            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText);
            this.checkForError(json);
            return new URI(json.get("data").index(0).get("media").index(0).get("sources").index(0).get("url").text());
        }
    }

    private byte[] getTrackDecryptionKey() throws NoSuchAlgorithmException {
        char[] md5 = Hex.encodeHex(MessageDigest.getInstance("MD5").digest(this.trackInfo.identifier.getBytes()), true);
        byte[] key = new byte[16];

        for (int i = 0; i < 16; i++) {
            key[i] = (byte) (md5[i] ^ md5[i + 16] ^ DeezerConstants.BLOWFISH.charAt(i));
        }

        return key;
    }

    private void checkForError(JsonBrowser json) {
        JsonBrowser error = json.get("data").index(0).get("errors").index(0);
        if (error.get("code").asLong(0) != 0) {
            throw new FriendlyException("Error while loading track: " + error.get("message").text(), FriendlyException.Severity.COMMON, null);
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new DeezerAudioTrack(this.trackInfo, this.sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return this.sourceManager;
    }
}
