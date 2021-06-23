package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;
import static java.nio.charset.StandardCharsets.UTF_8;

public class DefaultMixcloudDirectUrlLoader implements MixcloudDirectUrlLoader {

    private final HttpInterfaceManager httpInterfaceManager;

    public DefaultMixcloudDirectUrlLoader() {
        this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
    }

    public ExtendedHttpConfigurable getHttpConfiguration() {
        return httpInterfaceManager;
    }

    @Override
    public String getStreamUrl(String url) {
        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            /**
             * This URL is from a random mixcloud downloader...
             */
            HttpPost post = new HttpPost("https://www.dlmixcloud.com/ajax.php");
            post.setEntity(new StringEntity(String.format("{\"url\":\"%s\"}", url), "UTF-8"));
            try (CloseableHttpResponse response = httpInterface.execute(post)) {
                HttpClientTools.assertSuccessWithContent(response, "track response");

                String responseText = EntityUtils.toString(response.getEntity(), UTF_8);
                JsonBrowser jsonBrowser = JsonBrowser.parse(responseText);

                if(jsonBrowser.get("url").isNull()) throw ExceptionTools.wrapUnfriendlyExceptions("Failed to get Mixcloud song url", SUSPICIOUS, null);

                return jsonBrowser.get("url").text();
            }
        } catch(Exception e) {
            throw ExceptionTools.wrapUnfriendlyExceptions("Failed to load Mixcloud stream.", SUSPICIOUS, e);
        }
    }
}
