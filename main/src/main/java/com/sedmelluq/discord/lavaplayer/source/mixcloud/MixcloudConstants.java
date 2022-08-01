package com.sedmelluq.discord.lavaplayer.source.mixcloud;

public class MixcloudConstants {
    static final String ORIGIN_URL = "https://www.mixcloud.com";
    static final String ARTIST_URL = ORIGIN_URL + "/%s";
    static final String PLAYLIST_URL = ORIGIN_URL + "/%s/playlists/%s/";
    static final String GRAPHQL_URL = ORIGIN_URL + "/graphql";

    //Graphql handler
    static final String TRACK_PAYLOAD = "{\"query\":\"query cloudcastQuery($lookup: CloudcastLookup!) { cloudcast: cloudcastLookup(lookup: $lookup) { url audioLength name isExclusive waveformUrl previewUrl restrictedReason owner { displayName } streamInfo { url dashUrl hlsUrl } picture(width: 1024, height: 1024) { url } } }\",\"variables\":{\"lookup\":{\"slug\":\"%s\",\"username\":\"%s\"}}}";
    static final String ARTIST_PAYLOAD = "{\"query\":\"query UserUploadsQuery($lookup: UserLookup!) { user: userLookup(lookup: $lookup) { displayName username picture(width: 1024, height: 1024) { url } uploads(first: 100) { edges { node { name isExclusive waveformUrl previewUrl owner { displayName } streamInfo { url dashUrl hlsUrl } url restrictedReason audioLength  picture(width: 1024, height: 1024) { url } } } }  }  }\",\"variables\":{\"lookup\":{\"username\":\"%s\"}}}";
    static final String PLAYLIST_PAYLOAD = "{\"query\":\"query UserPlaylistQuery($lookup: PlaylistLookup!) { playlist: playlistLookup(lookup: $lookup) { name owner { displayName username } picture(width: 1024, height: 1024) { url } slug items { edges { node { cloudcast { name isExclusive waveformUrl previewUrl restrictedReason owner { displayName } streamInfo { url dashUrl hlsUrl }  url audioLength  picture(width: 1024, height: 1024) { url } } } } } } }\",\"variables\":{\"lookup\":{\"slug\":\"%s\",\"username\":\"%s\"}}}";
    static final String SEARCH_PAYLOAD = "{\"query\":\"query SearchCloudcastResultsQuery($term: String!) { viewer { search { searchQuery(term: $term) { cloudcasts(first: 100) { edges { node { name isExclusive waveformUrl previewUrl restrictedReason owner { displayName } streamInfo { url dashUrl hlsUrl } url audioLength picture(width: 1024, height: 1024) { url } } } } } } }  }\",\"variables\":{ \"term\": \"%s\" }}";

    //Audio processing
    static final String DECRYPTION_KEY = "IFYOUWANTTHEARTISTSTOGETPAIDDONOTDOWNLOADFROMMIXCLOUD";
    //static final String MANIFEST_AUDIO_URL = "https://audio%s.mixcloud.com/secure/dash2/%s.m4a/manifest.mpd";
    //static final String HLS_AUDIO_URL = "https://audio%s.mixcloud.com/secure/hls/%s.m4a/index.m3u8";
}