package com.sedmelluq.discord.lavaplayer.source.google.tts;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoBuilder;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleTTSAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String TTS_PREFIX = "googletts:";
    private static final String PREFIX_REGEX = "googletts:(?<language>[a-zA-Z-\\-]{2}:|[a-zA-Z-\\-]{5}:|)(.*)";
    private static final Pattern prefixPattern = Pattern.compile(PREFIX_REGEX);
    private final String language;
    private final HttpInterfaceManager httpInterfaceManager;

    public GoogleTTSAudioSourceManager() {
        this("en-US");
    }

    public GoogleTTSAudioSourceManager(String language) {
        this.language = language;

        httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "google-tts";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if (!reference.identifier.startsWith(TTS_PREFIX)) return null;
        Matcher m = prefixPattern.matcher(reference.identifier);
        if (!m.find()) return null;
        String language = m.group("language").isEmpty() ? this.language : m.group("language");
        String input = m.group(2);
        AudioTrackInfo trackInfo = AudioTrackInfoBuilder
                .empty()
                .apply(reference)
                .setIsStream(false)
                .setIdentifier(input)
                .build();
        return new GoogleTTSAudioTrack(trackInfo, language, this);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        GoogleTTSAudioTrack audioTrack = ((GoogleTTSAudioTrack) track);
        DataFormatTools.writeNullableText(output, audioTrack.language);
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new GoogleTTSAudioTrack(trackInfo, DataFormatTools.readNullableText(input), this);
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
    }

    public HttpInterface getInterface() {
        return httpInterfaceManager.getInterface();
    }
}
