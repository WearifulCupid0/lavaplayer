package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;
import static com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudConstants.GRAPHQL_URL;

public class MixcloudHelper {
    public static JsonBrowser requestGraphql(HttpInterface httpInterface, String body) {
        HttpPost post = new HttpPost(GRAPHQL_URL);
        StringEntity payload = new StringEntity(body, "UTF-8");
        post.setEntity(payload);
        post.setHeader("Content-Type", "application/json");
        try (CloseableHttpResponse response = httpInterface.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("Mixcloud graphql request failed with status code: " + statusCode);
            }

            String responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(responseText).get("data");

            if(!json.get("errors").isNull()) {
                throw new Exception(json.get("errors").index(0).get("message").text());
            }

            return json;
        } catch (Exception e) {
            throw new FriendlyException("Failed to make a request to Mixcloud Graphql", SUSPICIOUS, e);
        }
    }

    public static String getStreamUrl(HttpInterface httpInterface, String baseUrl) {
        int index = 0;
        String url = null;
        while (index++ < 18) {
            URI uri = URI.create(String.format(baseUrl, index));
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(uri))) {
                HttpClientTools.assertSuccessWithContent(response, "stream response");
                url = uri.toString();
                break;
            } catch(IOException e) {
                continue;
            }
        }
        return url;
    }
}
