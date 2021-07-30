package com.sedmelluq.discord.lavaplayer.source.tiktok;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import java.io.IOException;
import org.apache.commons.io.IOUtils;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class TikTokMediaExtractor {
    private static final String urlFormat = "https://www.tiktok.com/%s/video/%s";
    public JsonBrowser fetchFromPage(String user, String id, HttpInterface httpInterface) {
        try(CloseableHttpResponse response = httpInterface.execute(new HttpGet(String.format(urlFormat, user, id)))) {
            String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            Document document = Jsoup.parse(html);
            JsonBrowser json = JsonBrowser.parse(document.selectFirst("script[id=__NEXT_DATA__]").text());
            if(json == null) {
                return null;
            }
            JsonBrowser props = json.get("props").get("pageProps");

            if(props.get("statusCode").as(Long.class) == 10216) {
                throw new FriendlyException("This video is private.", COMMON, null);
            }
            JsonBrowser item = json.get("itemInfo").get("itemStruct");
            if(item.isNull()) {
                return null;
            }
            return item;
        } catch(IOException e) {
            throw new FriendlyException("Failed to load info for tiktok video.", SUSPICIOUS, null);
        }
    }
    public AudioTrackInfo buildInfo(JsonBrowser item) {
        JsonBrowser video = item.get("video");
        String author = item.get("author").get("nickname").text();
        String authorId = item.get("author").get("uniqueId").text();
        String id = item.get("id").text();
        String uri = String.format(urlFormat, "@" + authorId, id);
        String title = author != null ? author : id;
        String artwork = video.get("cover").text();
        String identifier = authorId + "/" + id;
        return new AudioTrackInfo(title, author, (long) (video.get("duration").as(Double.class) * 1000.0), identifier, false, uri, artwork);
    }
}
