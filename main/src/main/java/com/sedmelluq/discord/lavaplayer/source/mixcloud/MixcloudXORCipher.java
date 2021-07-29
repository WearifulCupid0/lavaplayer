package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import java.util.Base64;

import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.MIXCLOUD_DECRYPTION_KEY;

public class MixcloudXORCipher {
    private final String url;

    public MixcloudXORCipher(String url) {
        this.url = url;
    }

    public String getURL() {
        return this.url;
    }

    public String decode() {
        try {
            byte[] xor = Base64.getDecoder().decode(this.url);
            byte[] key = MIXCLOUD_DECRYPTION_KEY.getBytes();

            for (int i = 0; i < xor.length; i++) {
                xor[i] = (byte) (xor[i] ^ key[i % key.length]);
            }

            return new String(xor);
        } catch(Exception e) {
            return null;
        }
    }
}
