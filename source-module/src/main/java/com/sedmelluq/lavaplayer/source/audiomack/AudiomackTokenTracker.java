package com.sedmelluq.lavaplayer.source.audiomack;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class AudiomackTokenTracker {
    private static final String DEFAULT_AUDIOMACK_CONSUMER_KEY = "audiomack-js";
    private static final String DEFAULT_AUDIOMACK_CONSUMER_SECRET = "f3ac5b086f3eab260520d8e3049561e6";

    private static final String OAUTH_VERSION = "1.0";
    private static final String SIGNATURE_METHOD = "HMAC-SHA1";
    private static final String API_HOST = "api.audiomack.com";

    private final String consumerKey;
    private final String consumerSecret;
    private final SecureRandom random = new SecureRandom();

    private volatile String accessToken;
    private volatile String accessTokenSecret;
    private volatile long accessTokenExpiresAt;

    public AudiomackTokenTracker() {
        this(DEFAULT_AUDIOMACK_CONSUMER_KEY, DEFAULT_AUDIOMACK_CONSUMER_SECRET);
    }

    public AudiomackTokenTracker(String consumerKey, String consumerSecret) {
        if (isBlank(consumerKey) || isBlank(consumerSecret)) {
            consumerKey = DEFAULT_AUDIOMACK_CONSUMER_KEY;
            consumerSecret = DEFAULT_AUDIOMACK_CONSUMER_SECRET;
        }

        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
    }

    public AudiomackTokenTracker(
            String consumerKey,
            String consumerSecret,
            String accessToken,
            String accessTokenSecret,
            long accessTokenExpiresAt
    ) {
        this(consumerKey, consumerSecret);
        setAccessToken(accessToken, accessTokenSecret, accessTokenExpiresAt);
    }

    public boolean shouldSign(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return false;
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);

        return API_HOST.equals(host) || host.endsWith("." + API_HOST);
    }

    public boolean hasAccessToken() {
        return !isBlank(accessToken) && !isBlank(accessTokenSecret);
    }

    public boolean isAccessTokenExpired() {
        return accessTokenExpiresAt > 0 && System.currentTimeMillis() >= accessTokenExpiresAt;
    }

    public void setAccessToken(String accessToken, String accessTokenSecret, long accessTokenExpiresAt) {
        if (isBlank(accessToken)) {
            throw new IllegalArgumentException("Audiomack access token cannot be null or blank.");
        }

        if (isBlank(accessTokenSecret)) {
            throw new IllegalArgumentException("Audiomack access token secret cannot be null or blank.");
        }

        this.accessToken = accessToken;
        this.accessTokenSecret = accessTokenSecret;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public void clearAccessToken() {
        this.accessToken = null;
        this.accessTokenSecret = null;
        this.accessTokenExpiresAt = 0L;
    }

    public String createAuthorizationHeader(String method, URI uri) {
        return createAuthorizationHeader(method, uri, Collections.<Parameter>emptyList());
    }

    public String createAuthorizationHeader(String method, URI uri, List<Parameter> requestParameters) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(uri, "uri");

        String nonce = createNonce();
        String timestamp = Long.toString(System.currentTimeMillis() / 1000L);

        List<Parameter> oauthParameters = new ArrayList<>();
        oauthParameters.add(new Parameter("oauth_consumer_key", consumerKey));
        oauthParameters.add(new Parameter("oauth_nonce", nonce));
        oauthParameters.add(new Parameter("oauth_signature_method", SIGNATURE_METHOD));
        oauthParameters.add(new Parameter("oauth_timestamp", timestamp));
        oauthParameters.add(new Parameter("oauth_version", OAUTH_VERSION));

        String currentAccessToken = accessToken;

        if (!isBlank(currentAccessToken)) {
            oauthParameters.add(new Parameter("oauth_token", currentAccessToken));
        }

        List<Parameter> signatureParameters = new ArrayList<>();
        signatureParameters.addAll(oauthParameters);
        signatureParameters.addAll(parseQueryParameters(uri.getRawQuery()));

        if (requestParameters != null) {
            signatureParameters.addAll(requestParameters);
        }

        String signature = sign(method, uri, signatureParameters);
        oauthParameters.add(new Parameter("oauth_signature", signature));

        return buildAuthorizationHeader(oauthParameters);
    }

    public Parameter parameter(String name, String value) {
        return new Parameter(name, value);
    }

    private String sign(String method, URI uri, List<Parameter> parameters) {
        String normalizedMethod = method.toUpperCase(Locale.ROOT);
        String normalizedUrl = normalizeBaseUrl(uri);
        String normalizedParameters = normalizeParameters(parameters);

        String signatureBaseString =
                percentEncode(normalizedMethod) +
                        "&" +
                        percentEncode(normalizedUrl) +
                        "&" +
                        percentEncode(normalizedParameters);

        String signingKey =
                percentEncode(consumerSecret) +
                        "&" +
                        percentEncode(accessTokenSecret == null ? "" : accessTokenSecret);

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));

            byte[] signature = mac.doFinal(signatureBaseString.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign Audiomack OAuth request.", e);
        }
    }

    private static String normalizeBaseUrl(URI uri) {
        String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? API_HOST : uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();

        StringBuilder builder = new StringBuilder();

        builder.append(scheme).append("://").append(host);

        if (port != -1 && !isDefaultPort(scheme, port)) {
            builder.append(':').append(port);
        }

        String path = uri.getRawPath();

        if (path == null || path.isEmpty()) {
            builder.append('/');
        } else {
            builder.append(path);
        }

        return builder.toString();
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }

    private static String normalizeParameters(List<Parameter> parameters) {
        List<Parameter> encoded = new ArrayList<>();

        for (Parameter parameter : parameters) {
            if (parameter == null || parameter.name == null) {
                continue;
            }

            encoded.add(new Parameter(
                    percentEncode(parameter.name),
                    percentEncode(parameter.value == null ? "" : parameter.value)
            ));
        }

        encoded.sort(
                Comparator
                        .comparing((Parameter parameter) -> parameter.name)
                        .thenComparing(parameter -> parameter.value)
        );

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < encoded.size(); i++) {
            if (i > 0) {
                builder.append('&');
            }

            Parameter parameter = encoded.get(i);
            builder.append(parameter.name).append('=').append(parameter.value);
        }

        return builder.toString();
    }

    private static List<Parameter> parseQueryParameters(String rawQuery) {
        List<Parameter> parameters = new ArrayList<>();

        if (rawQuery == null || rawQuery.isEmpty()) {
            return parameters;
        }

        String[] pairs = rawQuery.split("&");

        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }

            int equalsIndex = pair.indexOf('=');

            if (equalsIndex == -1) {
                parameters.add(new Parameter(urlDecode(pair), ""));
            } else {
                String name = pair.substring(0, equalsIndex);
                String value = pair.substring(equalsIndex + 1);

                parameters.add(new Parameter(urlDecode(name), urlDecode(value)));
            }
        }

        return parameters;
    }

    private String buildAuthorizationHeader(List<Parameter> oauthParameters) {
        List<Parameter> sorted = new ArrayList<>(oauthParameters);
        sorted.sort(Comparator.comparing(parameter -> parameter.name));

        StringBuilder builder = new StringBuilder("OAuth ");

        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }

            Parameter parameter = sorted.get(i);

            builder
                    .append(percentEncode(parameter.name))
                    .append("=\"")
                    .append(percentEncode(parameter.value))
                    .append('"');
        }

        return builder.toString();
    }

    private String createNonce() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String percentEncode(String value) {
        if (value == null) {
            return "";
        }

        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private static String urlDecode(String value) {
        if (value == null) {
            return "";
        }

        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Parameter {
        public final String name;
        public final String value;

        public Parameter(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}