package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.STREAMINFO_PAYLOAD;

public class DefaultMixcloudDirectUrlLoader extends AbstractMixcloudApiLoader implements MixcloudDirectUrlLoader {
    @Override
    public String getStreamUrl(String identifier) {
        String[] splitted = identifier.split("/");
        String slug = splitted[0];
        String username = splitted[1];
        return extractFromApi(String.format(STREAMINFO_PAYLOAD, username, slug), (httpClient, data) -> {
            String text = data.get("cloudcast").get("streamInfo").get("url").text();
            if(text == null) {
                return null;
            }
            MixcloudXORCipher cipher = new MixcloudXORCipher(text);
            return cipher.decode();
        });
    }
}
