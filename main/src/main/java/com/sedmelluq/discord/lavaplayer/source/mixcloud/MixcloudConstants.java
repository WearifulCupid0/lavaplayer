package com.sedmelluq.discord.lavaplayer.source.mixcloud;

public class MixcloudConstants {

    static final String MIXCLOUD_API_URL = "https://www.mixcloud.com/graphql";
    static final String MIXCLOUD_PUBLIC_API_URL = "https://api.mixcloud.com/";

    static final String MIXCLOUD_SEARCH_API = MIXCLOUD_PUBLIC_API_URL + "search/?type=cloudcast&q=";

    static final String TRACK_PAYLOAD = "{\"query\":\"query cloudcastQuery($lookup: CloudcastLookup!) { cloudcast: cloudcastLookup(lookup: $lookup) { url audioLength name owner { displayName } picture(width: 1024, height: 1024) { url } } }\",\"variables\":{\"lookup\":{\"username\":\"%s\",\"slug\":\"%s\"}}}";
    static final String ARTIST_PAYLOAD = "{\"query\":\"query UserUploadsQuery($lookup: UserLookup!) { user: userLookup(lookup: $lookup) { displayName uploads(first: 100) { edges { node { name owner { displayName } url audioLength  picture(width: 1024, height: 1024) { url } } } }  }  }\",\"variables\":{\"lookup\":{\"username\":\"%s\"}}}";
    static final String PLAYLIST_PAYLOAD = "{\"query\":\"query UserPlaylistQuery($lookup: PlaylistLookup!) { playlist: playlistLookup(lookup: $lookup) { name items { edges { node { cloudcast { name owner { displayName } url audioLength  picture(width: 1024, height: 1024) { url } } } } } } }\",\"variables\":{\"lookup\":{\"username\":\"%s\",\"slug\":\"%s\"}}}";

}