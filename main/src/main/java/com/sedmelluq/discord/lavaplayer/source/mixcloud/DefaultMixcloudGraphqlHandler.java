package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.*;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DefaultMixcloudGraphqlHandler implements MixcloudGraphqlHandler {
    private final MixcloudAudioSourceManager sourceManager;

    public DefaultMixcloudGraphqlHandler(MixcloudAudioSourceManager sourceManager) {
        this.sourceManager = sourceManager;
    }

    @Override
    public AudioTrack processAsSigleTrack(String slug, String username) {
        try(HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            HttpPost post = new HttpPost(GRAPHQL_URL);
            StringEntity payload = new StringEntity(String.format(TRACK_PAYLOAD, slug, username), "UTF-8");
            post.setEntity(payload);
            post.setHeader("Content-Type", "application/json");
            try(CloseableHttpResponse response = httpInterface.execute(post)) {
                HttpClientTools.assertSuccessWithContent(response, "track response");

                String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                JsonBrowser jsonBrowser = JsonBrowser.parse(responseText).get("data");

                if(!jsonBrowser.get("errors").isNull()) {
                    throw new Exception(jsonBrowser.get("errors").index(0).get("message").text());
                }

                JsonBrowser trackData = jsonBrowser.get("cloudcast");
                if(trackData.isNull()) return null;

                if(sourceManager.dataReader.isTrackPlayable(trackData)) {
                    List<MixcloudTrackFormat> formats = sourceManager.dataReader.readTrackFormats(trackData);
                    MixcloudTrackFormat bestFormat = sourceManager.formatHandler.chooseBestFormat(formats);
                    String identifier = sourceManager.formatHandler.buildFormatIdentifier(bestFormat);
                    if (identifier != null) return new MixcloudAudioTrack(sourceManager.dataReader.readTrackInfo(trackData, identifier), sourceManager);
                }

                return null;
            }
        } catch(Exception e) {
            throw new FriendlyException("Failed to load mixcloud track", SUSPICIOUS, e);
        }
    }

    @Override
    public AudioPlaylist processPlaylist(String slug, String username) {
        try(HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            if(slug == null && username != null) return processArtist(username, httpInterface);
            HttpPost post = new HttpPost(GRAPHQL_URL);
            StringEntity payload = new StringEntity(String.format(PLAYLIST_PAYLOAD, slug, username), "UTF-8");
            post.setEntity(payload);
            post.setHeader("Content-Type", "application/json");
            try(CloseableHttpResponse response = httpInterface.execute(post)) {
                HttpClientTools.assertSuccessWithContent(response, "playlist response");

                String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                JsonBrowser jsonBrowser = JsonBrowser.parse(responseText).get("data");
                
                if(!jsonBrowser.get("errors").isNull()) {
                    throw new Exception(jsonBrowser.get("errors").index(0).get("message").text());
                }

                JsonBrowser playlistData = jsonBrowser.get("playlist");
                if(playlistData.isNull()) return null;
                
                List<AudioTrack> tracks = new ArrayList<>();

                playlistData.get("items").get("edges").values()
                .forEach(edge -> {
                    JsonBrowser trackData = edge.get("node").get("cloudcast");
                    if(sourceManager.dataReader.isTrackPlayable(trackData)) {
                        List<MixcloudTrackFormat> formats = sourceManager.dataReader.readTrackFormats(trackData);
                        MixcloudTrackFormat bestFormat = sourceManager.formatHandler.chooseBestFormat(formats);
                        String identifier = sourceManager.formatHandler.buildFormatIdentifier(bestFormat);
                        if (identifier != null) tracks.add(new MixcloudAudioTrack(sourceManager.dataReader.readTrackInfo(trackData, identifier), sourceManager));
                    }
                });

                return new BasicAudioPlaylist(
                    playlistData.get("name").text(),
                    playlistData.get("owner").get("displayName").text(),
                    playlistData.get("picture").get("url").text(),
                    String.format(PLAYLIST_URL, playlistData.get("owner").get("username").text(), playlistData.get("slug").text()),
                    "playlist", tracks, null, false
                );
            }
        } catch(Exception e) {
            throw new FriendlyException("Failed to load mixcloud playlist", SUSPICIOUS, e);
        }
    }

    private AudioPlaylist processArtist(String slug, HttpInterface httpInterface) throws Exception {
        HttpPost post = new HttpPost(GRAPHQL_URL);
        StringEntity payload = new StringEntity(String.format(ARTIST_PAYLOAD, slug), "UTF-8");
        post.setEntity(payload);
        post.setHeader("Content-Type", "application/json");
        try (CloseableHttpResponse response = httpInterface.execute(post)) {
            HttpClientTools.assertSuccessWithContent(response, "artist response");

            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser jsonBrowser = JsonBrowser.parse(responseText).get("data");
                
            if(!jsonBrowser.get("errors").isNull()) {
                throw new Exception(jsonBrowser.get("errors").index(0).get("message").text());
            }

            JsonBrowser artistData = jsonBrowser.get("user");
            if(artistData.isNull()) return null;

            List<AudioTrack> tracks = new ArrayList<>();

            artistData.get("uploads").get("edges").values()
            .forEach(edge -> {
                JsonBrowser trackData = edge.get("node");
                if(sourceManager.dataReader.isTrackPlayable(trackData)) {
                    List<MixcloudTrackFormat> formats = sourceManager.dataReader.readTrackFormats(trackData);
                    MixcloudTrackFormat bestFormat = sourceManager.formatHandler.chooseBestFormat(formats);
                    String identifier = sourceManager.formatHandler.buildFormatIdentifier(bestFormat);
                    if (identifier != null) tracks.add(new MixcloudAudioTrack(sourceManager.dataReader.readTrackInfo(trackData, identifier), sourceManager));
                }
            });

            return new BasicAudioPlaylist(
                artistData.get("displayName").text(),
                artistData.get("displayName").text(),
                artistData.get("picture").get("url").text(),
                String.format(ARTIST_URL, artistData.get("username").text()),
                "artist", tracks, null, false
            );
        }
    }

    @Override
    public AudioPlaylist processSearch(String query) {
        try(HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            HttpPost post = new HttpPost(GRAPHQL_URL);
            StringEntity payload = new StringEntity(String.format(SEARCH_PAYLOAD, query.replace("\"", "\\\"")), "UTF-8");
            post.setEntity(payload);
            post.setHeader("Content-Type", "application/json");
            try (CloseableHttpResponse response = httpInterface.execute(post)) {
                HttpClientTools.assertSuccessWithContent(response, "search response");

                String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                JsonBrowser jsonBrowser = JsonBrowser.parse(responseText).get("data");
                
                if(!jsonBrowser.get("errors").isNull()) {
                    throw new Exception(jsonBrowser.get("errors").index(0).get("message").text());
                }

                JsonBrowser edges = jsonBrowser.get("viewer").get("search").get("searchQuery").get("cloudcasts").get("edges");
                if(edges.index(0).isNull()) return null;

                List<AudioTrack> tracks = new ArrayList<>();

                edges.values()
                .forEach(edge -> {
                    JsonBrowser trackData = edge.get("node");
                    if(sourceManager.dataReader.isTrackPlayable(trackData)) {
                        List<MixcloudTrackFormat> formats = sourceManager.dataReader.readTrackFormats(trackData);
                        MixcloudTrackFormat bestFormat = sourceManager.formatHandler.chooseBestFormat(formats);
                        String identifier = sourceManager.formatHandler.buildFormatIdentifier(bestFormat);
                        if (identifier != null) tracks.add(new MixcloudAudioTrack(sourceManager.dataReader.readTrackInfo(trackData, identifier), sourceManager));
                    }
                });

                return new BasicAudioPlaylist("Search results for: " + query, null, null, null, "search", tracks, null, true);
            }
        } catch(Exception e) {
            throw new FriendlyException("Failed to load mixcloud search results", SUSPICIOUS, e);
        }
    }
}