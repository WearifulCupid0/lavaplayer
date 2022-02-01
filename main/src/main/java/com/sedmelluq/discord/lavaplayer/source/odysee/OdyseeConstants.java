package com.sedmelluq.discord.lavaplayer.source.odysee;

public class OdyseeConstants {
  static final String ODYSEE_AUTH_KEY = "5TKhwG82zQRQ9NpyBkPdxc6QtgFJ5b4i";

  static final String API_URL = "https://api.na-backend.odysee.com/api/v1/proxy";
  static final String SEARCH_URL = "https://lighthouse.odysee.com/search";

  // Used to get track info
  static final String RESOLVE_PAYLOAD = "{\"method\": \"resolve\", \"params\": { \"urls\": [\"%s\"] } }";
  // Used to get track playback URL
  static final String GET_PAYLOAD = "{\"method\": \"get\", \"params\": { \"uri\": \"%s\", \"save_file\": false } }";
}