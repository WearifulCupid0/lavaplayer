package com.sedmelluq.discord.lavaplayer.source.mixcloud;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.STREAMINFO_PAYLOAD;

public class DefaultMixcloudDirectUrlLoader extends AbstractMixcloudApiLoader implements MixcloudDirectUrlLoader {
    private final String TRACK_REGEX = "(?:http://|https://|)?(?:(?:www|beta|m)\\.)?mixcloud\\.com/([^/]+)/([^/]+)";
    private final Pattern trackPattern = Pattern.compile(TRACK_REGEX);

    @Override
    public String getStreamUrl(String url) {
        Matcher matcher = trackPattern.matcher(url);
        String slug = matcher.group(2);
        String username = matcher.group(1);
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
