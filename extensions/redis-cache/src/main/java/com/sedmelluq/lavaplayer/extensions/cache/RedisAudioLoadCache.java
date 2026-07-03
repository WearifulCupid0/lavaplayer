package com.sedmelluq.lavaplayer.extensions.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.lavaplayer.extensions.cache.policy.CachePolicy;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;

public class RedisAudioLoadCache implements AudioLoadCache {
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> redis;
    private final ObjectMapper mapper;
    private final CachePolicy policy;

    public RedisAudioLoadCache(String redisUri, CachePolicy policy) {
        this.redisClient = RedisClient.create(redisUri);
        this.connection = redisClient.connect();
        this.redis = connection.sync();
        this.mapper = new ObjectMapper();
        this.policy = policy;
    }

    @Override
    public CachedLoadResult get(AudioPlayerManager manager, AudioReference reference) throws Exception {
        String value = redis.get(key(reference));

        if (value == null || value.isEmpty()) {
            return null;
        }

        return mapper.readValue(value, CachedLoadResult.class);
    }

    @Override
    public void putTrack(AudioPlayerManager manager, AudioReference reference, AudioTrack track) throws Exception {
        if (!policy.shouldCacheTrack(reference, track)) {
            return;
        }

        CachedLoadResult result = CachedLoadResult.track(manager, track);
        put(reference, result, policy.trackTtl(track));
    }

    @Override
    public void putPlaylist(AudioPlayerManager manager, AudioReference reference, AudioPlaylist playlist) throws Exception {
        if (!policy.shouldCachePlaylist(reference, playlist)) {
            return;
        }

        CachedLoadResult result = CachedLoadResult.playlist(manager, playlist);
        put(reference, result, policy.playlistTtl(playlist));
    }

    @Override
    public void putNoMatches(AudioReference reference) throws Exception {
        if (!policy.shouldCacheNoMatches(reference)) {
            return;
        }

        put(reference, CachedLoadResult.noMatches(), policy.noMatchesTtl());
    }

    @Override
    public void invalidate(AudioReference reference) {
        redis.del(key(reference));
    }

    private void put(AudioReference reference, CachedLoadResult result, Duration ttl) throws Exception {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }

        String json = mapper.writeValueAsString(result);

        redis.set(
                key(reference),
                json,
                SetArgs.Builder.ex(ttl.getSeconds())
        );
    }

    private String key(AudioReference reference) {
        String identifier = normalizeIdentifier(reference.identifier);
        String container = reference.containerDescriptor != null ? reference.containerDescriptor.getClass().getName() : "none";

        return "lavaplayer:load:v1:" + sha256(container + ":" + identifier);
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }

        return identifier
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder builder = new StringBuilder(hash.length * 2);

            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }

            return builder.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        connection.close();
        redisClient.shutdown();
    }
}