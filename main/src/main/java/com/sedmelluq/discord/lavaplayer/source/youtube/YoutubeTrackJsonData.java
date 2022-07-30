package com.sedmelluq.discord.lavaplayer.source.youtube;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.sedmelluq.discord.lavaplayer.tools.ExceptionTools.throwWithDebugInfo;
import static com.sedmelluq.discord.lavaplayer.tools.JsonBrowser.NULL_BROWSER;

public class YoutubeTrackJsonData {
  private static final Logger log = LoggerFactory.getLogger(DefaultYoutubeTrackDetailsLoader.class);

  public final JsonBrowser playerResponse;
  public final JsonBrowser polymerArguments;
  public final String playerScriptUrl;

  public final Boolean explicit;

  public YoutubeTrackJsonData(JsonBrowser playerResponse, JsonBrowser polymerArguments, String playerScriptUrl, Boolean explicit) {
    this.playerResponse = playerResponse;
    this.polymerArguments = polymerArguments;
    this.playerScriptUrl = playerScriptUrl;
    this.explicit = explicit;
  }

  public YoutubeTrackJsonData withPlayerScriptUrl(String playerScriptUrl, Boolean explicit) {
    return new YoutubeTrackJsonData(playerResponse, polymerArguments, playerScriptUrl, explicit);
  }

  public static YoutubeTrackJsonData fromMainResult(JsonBrowser result, Boolean explicit) {
    try {
      JsonBrowser playerInfo = NULL_BROWSER;
      JsonBrowser playerResponse = NULL_BROWSER;

      List<JsonBrowser> json = result.values();
      JsonBrowser lastElement = json.get(result.values().size() - 1);
      if (!lastElement.get("page").isNull()) {
        for (JsonBrowser child : result.values()) {
          if (child.isMap()) {
            if (playerInfo.isNull()) {
              playerInfo = child.get("player");
            }

            if (playerResponse.isNull()) {
              playerResponse = child.get("playerResponse");
            }
          }
        }
      } else {
        if (playerResponse.isNull()) {
          playerResponse = result;
        }
      }

      if (!playerInfo.isNull()) {
        return fromPolymerPlayerInfo(playerInfo, playerResponse, explicit);
      } else if (!playerResponse.isNull()) {
        return new YoutubeTrackJsonData(playerResponse, NULL_BROWSER, null, explicit);
      }
    } catch (Exception e) {
      throw throwWithDebugInfo(log, e, "Error parsing result", "json", result.format());
    }

    throw throwWithDebugInfo(log, null, "Neither player nor playerResponse in result", "json", result.format());
  }

  private static YoutubeTrackJsonData fromPolymerPlayerInfo(JsonBrowser playerInfo, JsonBrowser playerResponse, Boolean explicit) {
    JsonBrowser args = playerInfo.get("args");
    String playerScriptUrl = playerInfo.get("assets").get("js").text();

    String playerResponseText = args.get("player_response").text();

    if (playerResponseText == null) {
      // In case of Polymer, the playerResponse with formats is the one embedded in args, NOT the one in outer JSON.
      // However, if no player_response is available, use the outer playerResponse.
      return new YoutubeTrackJsonData(playerResponse, args, playerScriptUrl, explicit);
    }

    return new YoutubeTrackJsonData(parsePlayerResponse(playerResponseText), args, playerScriptUrl, explicit);
  }

  private static JsonBrowser parsePlayerResponse(String playerResponseText) {
    try {
      return JsonBrowser.parse(playerResponseText);
    } catch (Exception e) {
      throw throwWithDebugInfo(log, e, "Failed to parse player_response", "value", playerResponseText);
    }
  }
}