package com.sedmelluq.discord.lavaplayer.source.twitter;

public class TwitterConstants {
    static final String TWITTER_AUTH_KEY = "AAAAAAAAAAAAAAAAAAAAAPYXBAAAAAAACLXUNDekMxqa8h%2F40K4moUkGsoc%3DTYfbDKbT3jJPCEVnMYqilB28NHfOPqkca3qaAxGfsyKCs0wRbw";

    static final String API_URL = "https://api.twitter.com/1.1";
    static final String AUTH_URL = API_URL + "/guest/activate.json";
    static final String STATUS_URL = API_URL + "/statuses/show/%s.json?cards_platform=Web-12&include_cards=1&include_reply_count=1&include_user_entities=0&tweet_mode=extended";
}
