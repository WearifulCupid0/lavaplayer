package com.sedmelluq.discord.lavaplayer.source.soundcloud;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.PBJUtils;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;

public class DefaultSoundCloudDataReader implements SoundCloudDataReader {
  private static final Logger log = LoggerFactory.getLogger(DefaultSoundCloudDataReader.class);

  @Override
  public JsonBrowser findTrackData(JsonBrowser rootData) {
    return findEntryOfKind(rootData, "track");
  }

  @Override
  public String readTrackId(JsonBrowser trackData) {
    return trackData.get("id").safeText();
  }

  @Override
  public boolean isTrackBlocked(JsonBrowser trackData) {
    return "BLOCK".equals(trackData.get("policy").safeText());
  }

  @Override
  public AudioTrackInfo readTrackInfo(JsonBrowser trackData, String identifier) {
    return new AudioTrackInfo(
        trackData.get("title").safeText(),
        trackData.get("user").get("username").safeText(),
        trackData.get("duration").as(Integer.class),
        identifier,
        false,
        trackData.get("permalink_url").text(),
        PBJUtils.getSoundCloudThumbnail(trackData)
    );
  }

  @Override
  public JSONObject readTrackRichInfo(JsonBrowser trackData) {
    JSONObject json = new JSONObject();

    if(!trackData.get("playback_count").isNull()) json.put("plays", trackData.get("playback_count").as(Double.class));
    if(!trackData.get("likes_count").isNull()) json.put("likes", trackData.get("likes_count").as(Double.class));
    if(!trackData.get("reposts_count").isNull()) json.put("reposts", trackData.get("reposts_count").as(Double.class));
    if(!trackData.get("comment_count").isNull()) json.put("comments", trackData.get("comment_count").as(Double.class));
    if(!trackData.get("download_count").isNull()) json.put("downloads", trackData.get("download_count").as(Double.class));
    if(!trackData.get("created_at").isNull()) json.put("createdAt", trackData.get("created_at").text());
    if(!trackData.get("last_modified").isNull()) json.put("updatedAt", trackData.get("last_modified").text());
    if(!trackData.get("release_date").isNull()) json.put("releaseDate", trackData.get("release_date").text());

    JSONObject author = new JSONObject();
    JsonBrowser user = trackData.get("user");

    if(!user.get("followers_count").isNull()) author.put("followers", user.get("followers_count").as(Double.class));
    if(!user.get("last_modified").isNull()) author.put("updatedAt", user.get("last_modified").text());
    if(!user.get("permalink_url").isNull()) author.put("uri", user.get("permalink_url").text());
    if(!user.get("city").isNull()) author.put("city", user.get("city").text());
    if(!user.get("country_code").isNull()) author.put("countryCode", user.get("country_code").text());

    if(author.length() > 0) json.put("author", author);

    return json;
  }

  @Override
  public List<SoundCloudTrackFormat> readTrackFormats(JsonBrowser trackData) {
    List<SoundCloudTrackFormat> formats = new ArrayList<>();
    String trackId = readTrackId(trackData);

    if (trackId.isEmpty()) {
      log.warn("Track data {} missing track ID: {}.", trackId, trackData.format());
    }

    for (JsonBrowser transcoding : trackData.get("media").get("transcodings").values()) {
      JsonBrowser format = transcoding.get("format");

      String protocol = format.get("protocol").safeText();
      String mimeType = format.get("mime_type").safeText();

      if (!protocol.isEmpty() && !mimeType.isEmpty()) {
        String lookupUrl = transcoding.get("url").safeText();

        if (!lookupUrl.isEmpty()) {
          formats.add(new DefaultSoundCloudTrackFormat(trackId, protocol, mimeType, lookupUrl));
        } else {
          log.warn("Transcoding of {} missing url: {}.", trackId, transcoding.format());
        }
      } else {
        log.warn("Transcoding of {} missing protocol/mimetype: {}.", trackId, transcoding.format());
      }
    }

    return formats;
  }

  @Override
  public JsonBrowser findPlaylistData(JsonBrowser rootData) {
    return findEntryOfKind(rootData, "playlist");
  }

  @Override
  public String readPlaylistName(JsonBrowser playlistData) {
    return playlistData.get("title").safeText();
  }

  @Override
  public String readPlaylistIdentifier(JsonBrowser playlistData) {
    return playlistData.get("permalink").safeText();
  }

  @Override
  public List<JsonBrowser> readPlaylistTracks(JsonBrowser playlistData) {
    return playlistData.get("tracks").values();
  }

  protected JsonBrowser findEntryOfKind(JsonBrowser data, String kind) {
    for (JsonBrowser value : data.values()) {
      for (JsonBrowser entry : value.get("data").values()) {
        if (entry.isMap() && kind.equals(entry.get("kind").text())) {
          return entry;
        }
      }
    }

    return data;
  }
}
