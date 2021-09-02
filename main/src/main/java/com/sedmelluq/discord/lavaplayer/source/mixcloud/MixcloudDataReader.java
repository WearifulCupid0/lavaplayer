package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.json.JSONObject;
import java.util.List;

public interface MixcloudDataReader {
    boolean isTrackPlayable(JsonBrowser trackData);

    AudioTrackInfo readTrackInfo(JsonBrowser trackData, String identifier);

    JSONObject readTrackRichInfo(JsonBrowser trackData);

    List<MixcloudTrackFormat> readTrackFormats(JsonBrowser trackData);
}