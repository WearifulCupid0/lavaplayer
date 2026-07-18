package com.sedmelluq.lavaplayer.extensions.thirdpartysources;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.applemusic.AppleMusicAudioSourceManager;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.pandora.PandoraAudioSourceManager;
import com.sedmelluq.lavaplayer.extensions.thirdpartysources.tidal.TidalAudioSourceManager;
import org.apache.http.client.config.RequestConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ThirdPartySourceManagerHealthCheck {
    private static final int TIMEOUT_SECONDS = Integer.getInteger("sm.timeout", 45);

    public static void main(String[] args) {
        List<Case> cases = new ArrayList<>();

        cases.add(new Case(
                "applemusic",
                AppleMusicAudioSourceManager::new,
                "https://music.apple.com/br/album/we-never-dated/1832087626?i=1832088004&l=en-GB"
        ));

        /*cases.add(new Case(
                "deezer",
                DeezerAudioSourceManager::new,
                "https://link.deezer.com/s/33Jn0bxylovguAzkiM1SD"
        ));*/

        /*cases.add(new Case(
                "spotify",
                SpotifyAudioSourceManager::new,
                "https://open.spotify.com/track/2BqfIlpahcebJPeu1IUTEo?si=2f01a169b9d94109"
        ));*/

        cases.add(new Case(
                "pandora",
                PandoraAudioSourceManager::new,
                "https://www.pandora.com/artist/sombr/we-never-dated/we-never-dated/TRbXlpZj7w467VJ?part=ug-desktop&corr=208736787122632984"
        ));

        cases.add(new Case(
                "tidal",
                TidalAudioSourceManager::new,
                "https://tidal.com/track/455598291/u"
        ));

        System.out.println("| Source | Status | Details |");
        System.out.println("|---|---:|---|");

        for (Case testCase : cases) {
            if (testCase.identifier == null || testCase.identifier.isBlank()) {
                System.out.printf("| %s | SKIP | sem URL de teste confiável definida |%n", testCase.name);
                continue;
            }

            Result result = runCase(testCase);

            System.out.printf(
                    "| %s | %s | %s |%n",
                    testCase.name,
                    result.status,
                    sanitize(result.details)
            );
        }
    }

    private static AudioPlayerManager createManager() {
        AudioPlayerManager manager = new DefaultAudioPlayerManager();

        manager.setHttpRequestConfigurator(config -> RequestConfig.copy(config)
                .setConnectTimeout(10_000)
                .setSocketTimeout(20_000)
                .setConnectionRequestTimeout(10_000)
                .build());

        manager.setHttpBuilderConfigurator(builder -> builder
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/120.0.0.0 Safari/537.36")
        );

        return manager;
    }

    private static Result runCase(Case testCase) {
        AudioPlayerManager manager = createManager();
        Future<Void> future = null;

        try {
            manager.registerSourceManager(testCase.factory.create(manager));

            Handler handler = new Handler();

            future = manager.loadItem(testCase.identifier, handler);
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (handler.result == null) {
                return new Result("FAIL", "handler não retornou resultado");
            }

            return handler.result;
        } catch (TimeoutException e) {
            if (future != null) {
                future.cancel(true);
            }

            return new Result(
                    "TIMEOUT",
                    "timeout after " + TIMEOUT_SECONDS + "s while loading " + testCase.identifier
            );
        } catch (Exception e) {
            if (future != null) {
                future.cancel(true);
            }

            return new Result("FAIL", e.getClass().getSimpleName() + ": " + rootMessage(e));
        } finally {
            manager.shutdown();
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null ? current.toString() : message;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("|", "\\|")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    @FunctionalInterface
    private interface SourceFactory {
        AudioSourceManager create(AudioPlayerManager manager);
    }

    private static class Handler implements AudioLoadResultHandler {
        private Result result;

        @Override
        public void trackLoaded(AudioTrack track) {
            result = new Result("OK", "track: " + track.getInfo().title);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            result = new Result(
                    "OK",
                    "playlist: " + playlist.getName() + " / tracks=" + playlist.getTracks().size()
            );
        }

        @Override
        public void noMatches() {
            result = new Result("NO_MATCH", "nenhum resultado encontrado");
        }

        @Override
        public void loadFailed(FriendlyException exception) {
            result = new Result("FAIL", exception.severity + ": " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    private static class Result {
        private final String status;
        private final String details;

        private Result(String status, String details) {
            this.status = status;
            this.details = details;
        }
    }

    private static class Case {
        private final String name;
        private final SourceFactory factory;
        private final String identifier;

        private Case(String name, SourceFactory factory, String identifier) {
            this.name = name;
            this.factory = factory;
            this.identifier = identifier;
        }
    }
}