package com.sedmelluq.discord.lavaplayer.source.saavn;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static com.sedmelluq.discord.lavaplayer.source.saavn.SaavnConstants.API_URL;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class SaavnApiRequester {
    private final SaavnAudioSourceManager sourceManager;

    public SaavnApiRequester(SaavnAudioSourceManager sourceManager) {
        this.sourceManager = sourceManager;
    }

    public List<JsonBrowser> search(String query) {
        try (HttpInterface httpInterface = this.sourceManager.getHttpInterface()) {
            HttpGet get = new HttpGet(URI.create(API_URL + "&__call=search.getResults&p=1&n=30&q=" + query));
            try (CloseableHttpResponse response = httpInterface.execute(get)) {
                HttpClientTools.assertSuccessWithContent(response, "search results");

                JsonBrowser metadata = JsonBrowser.parse(response.getEntity().getContent());
                return metadata.get("results").values();
            }
        } catch(IOException e) {
            throw new FriendlyException("Saavn search information failed to load.", SUSPICIOUS, null);
        }
    }

    public JsonBrowser album(String id) {
        try (HttpInterface httpInterface = this.sourceManager.getHttpInterface()) {
            HttpGet get = new HttpGet(URI.create(API_URL + "&__call=webapi.get&includeMetaTags=0&type=album&token=" + id));
            try (CloseableHttpResponse response = httpInterface.execute(get)) {
                HttpClientTools.assertSuccessWithContent(response, "album result");

                JsonBrowser metadata = JsonBrowser.parse(response.getEntity().getContent());
                return metadata;
            }
        } catch(IOException e) {
            throw new FriendlyException("Saavn album information failed to load.", SUSPICIOUS, null);
        }
    }

    public JsonBrowser playlist(String id) {
        try (HttpInterface httpInterface = this.sourceManager.getHttpInterface()) {
            HttpGet get = new HttpGet(URI.create(API_URL + "&__call=webapi.get&includeMetaTags=0&p=1&n=1000&type=playlist&token=" + id));
            try (CloseableHttpResponse response = httpInterface.execute(get)) {
                HttpClientTools.assertSuccessWithContent(response, "playlist result");

                JsonBrowser metadata = JsonBrowser.parse(response.getEntity().getContent());
                return metadata;
            }
        } catch(IOException e) {
            throw new FriendlyException("Saavn playlist information failed to load.", SUSPICIOUS, null);
        }
    }

    public JsonBrowser track(String id) {
        try (HttpInterface httpInterface = this.sourceManager.getHttpInterface()) {
            HttpGet get = new HttpGet(URI.create(API_URL + "&__call=webapi.get&includeMetaTags=0&type=song&ctx=wap6dot0&token=" + id));
            try (CloseableHttpResponse response = httpInterface.execute(get)) {
                HttpClientTools.assertSuccessWithContent(response, "track result");

                JsonBrowser metadata = JsonBrowser.parse(response.getEntity().getContent());
                return metadata.get("songs").index(0);
            }
        } catch(IOException e) {
            throw new FriendlyException("Saavn track information failed to load.", SUSPICIOUS, null);
        }
    }

    public JsonBrowser decodeTrack(String encoded) {
        try (HttpInterface httpInterface = this.sourceManager.getHttpInterface()) {
            HttpGet get = new HttpGet(URI.create(API_URL + "&__call=song.generateAuthToken&url=" + encoded));
            try (CloseableHttpResponse response = httpInterface.execute(get)) {
                HttpClientTools.assertSuccessWithContent(response, "search results");

                JsonBrowser metadata = JsonBrowser.parse(response.getEntity().getContent());
                return metadata;
            }
        } catch(IOException e) {
            throw new FriendlyException("Saavn track information failed to load.", SUSPICIOUS, null);
        }
    }
}
