package com.sedmelluq.discord.lavaplayer.tools;

import java.util.List;

public class PBJUtils {

    public static String getYouTubeMusicThumbnail(JsonBrowser videoData, String videoId) {
        JsonBrowser thumbnails = videoData.get("thumbnail").get("thumbnails").index(0);
        if (!thumbnails.isNull()) return thumbnails.get("url").text().replaceFirst("=.*", "=w1000-h1000");
        return String.format("https://i.ytimg.com/vi_webp/%s/mqdefault.jpg", videoId);
    }

    public static String getYouTubeThumbnail(JsonBrowser videoData, String videoId) {
        List<JsonBrowser> thumbnails = videoData.get("thumbnail").get("thumbnails").values();
        if (!thumbnails.isEmpty()) {
            String lastThumbnail = thumbnails.get(thumbnails.size() - 1).get("url").text();

            if (lastThumbnail.contains("maxresdefault")) {
                return lastThumbnail;
            } else {
                return String.format("https://i.ytimg.com/vi/%s/mqdefault.jpg", videoId);
            }
        }
        return String.format("https://i.ytimg.com/vi/%s/mqdefault.jpg", videoId);
    }

    public static String getSoundCloudThumbnail(JsonBrowser trackData) {
        JsonBrowser thumbnail = trackData.get("artwork_url");
        if (!thumbnail.isNull()) return thumbnail.text().replace("large", "t500x500");
        JsonBrowser avatar = trackData.get("user").get("avatar_url");
        if (!avatar.isNull()) return avatar.text().replace("large", "t500x500");
        return null;
    }

    public static String getVimeoThumbnail(String id) {
        if (id != null) return "https://i.vimeocdn.com/video/" + id + "_1920x1080";
        return null;
    }

    public static String getBandcampArtwork(JsonBrowser trackData) {
        String artId = trackData.get("art_id").text();
        if (artId != null) {
            if (artId.length() < 10) {
                StringBuilder builder = new StringBuilder(artId);
                while (builder.length() < 10) {
                    builder.insert(0, "0");
                }
                artId = builder.toString();
            }
            return String.format("https://f4.bcbits.com/img/a%s_1.png", artId);
        }
        return null;
    }

    public static String getClypArtwork(JsonBrowser audioFile) {
        String artwork = null;
        if (!audioFile.get("ArtworkPictureUrl").isNull()) artwork = audioFile.get("ArtworkPictureUrl").text();
        if (artwork == null && !audioFile.get("User").get("ProfilePictureUrl").isNull()) artwork = audioFile.get("User").get("ProfilePictureUrl").text();
        return artwork;
    }
}