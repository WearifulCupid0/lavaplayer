package com.sedmelluq.lavaplayer.extensions.thirdpartysources;

public class SourceTools {
    public static String getStringOrEnv(String input, String env) {
        if (isBlank(input)) {
            return getPropertyOrEnv(env);
        }

        return input;
    }

    public static String getPropertyOrEnv(String name) {
        String property = System.getProperty(name);

        if (!isBlank(property)) {
            return property;
        }

        return System.getenv(name);
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty() ? null : normalized;
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }

        return null;
    }

    public static long parseLong(String value, long defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
