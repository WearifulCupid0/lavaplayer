package com.sedmelluq.discord.lavaplayer.source.iheart;

public class iHeartConstants {
    static final String BASE_API_URL = "https://api.iheart.com/api";

    static final String PODCAST_BASE_API_URL = BASE_API_URL + "/v3/podcast";
    static final String PODCASTS_API_URL = PODCAST_BASE_API_URL + "/podcasts/%s";
    static final String EPISODES_API_URL = PODCASTS_API_URL + "/episodes";
    static final String EPISODE_API_URL = PODCAST_BASE_API_URL + "/episodes/%s";

    static final String RADIO_API_URL = BASE_API_URL + "/v2/content/liveStations";
    static final String RADIO_SEARCH_API_URL = RADIO_API_URL + "?q=%s";
    static final String RADIO_ID_API_URL = RADIO_API_URL + "?id=%s";

    static final String IHEART_PODCAST_URL = "https://www.iheart.com/podcast/%s/";
    static final String IHEART_EPISODE_URL = IHEART_PODCAST_URL + "episode/%s";
}
