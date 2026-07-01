package com.sedmelluq.discord.lavaplayer.source;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.bandlab.BandlabAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.bilibili.BilibiliAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.clyp.ClypAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.deezer.DeezerAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.iheart.iHeartAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.jamendo.JamendoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.jiosaavn.JioSaavnAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.mixcloud.MixcloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.ocremix.OcremixAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.odysee.OdyseeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.reverbnation.ReverbnationAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.rumble.RumbleAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundgasm.SoundgasmAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.streamable.StreamableAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.tunein.TuneinAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.apache.http.client.config.RequestConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class SourceManagerHealthCheck {
    private static final int TIMEOUT_SECONDS = Integer.getInteger("sm.timeout", 45);

    public static void main(String[] args) {
        List<Case> cases = new ArrayList<>();

        cases.add(new Case("soundcloud", SoundCloudAudioSourceManager::createDefault,
                "scsearch:monstercat disconnected"));

        cases.add(new Case("http", () -> new HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY),
                "https://raw.githubusercontent.com/jiaaro/pydub/master/test/data/test1.mp3"));

        cases.add(new Case("bandcamp", BandcampAudioSourceManager::new,
                "https://catsystemcorp.bandcamp.com/album/lofi"));

        cases.add(new Case("vimeo", () -> new VimeoAudioSourceManager(),
                "https://vimeo.com/76979871"));

        cases.add(new Case("bilibili", () -> new BilibiliAudioSourceManager(true),
                "https://www.bilibili.com/video/BV1GJ411x7h7"));

        cases.add(new Case("deezer", () -> new DeezerAudioSourceManager(null, true),
                "https://www.deezer.com/track/3135556"));

        cases.add(new Case("jamendo", () -> new JamendoAudioSourceManager(true),
                "https://www.jamendo.com/track/1889981"));

        cases.add(new Case("mixcloud", () -> new MixcloudAudioSourceManager(true),
                "https://www.mixcloud.com/RickBragaDj/rick-braga-eletrofunk-carnaval-2025/"));

        cases.add(new Case("odysee", () -> new OdyseeAudioSourceManager(true),
                "https://odysee.com/@schypsy:b/Harry-Styles---Adore-you-(HD-Rip):d"));

        cases.add(new Case("iheart", () -> new iHeartAudioSourceManager(true, MediaContainerRegistry.DEFAULT_REGISTRY),
                "https://www.iheart.com/podcast/105-stuff-you-should-know-26940277/"));

        cases.add(new Case("jiosaavn", () -> new JioSaavnAudioSourceManager(true),
                "jssearch:alone marshmello"));

        cases.add(new Case("soundgasm", SoundgasmAudioSourceManager::new,
                ""));

        cases.add(new Case("streamable", StreamableAudioSourceManager::new,
                "https://streamable.com/moo"));

        cases.add(new Case("ocremix", OcremixAudioSourceManager::new,
                "https://ocremix.org/remix/OCR04243"));

        cases.add(new Case("tunein", TuneinAudioSourceManager::new,
                "https://tunein.com/radio/BBC-Radio-1-s24939/"));

        cases.add(new Case("twitch", TwitchStreamAudioSourceManager::new,
                "https://www.twitch.tv/monstercat"));

        cases.add(new Case("clyp", ClypAudioSourceManager::new,
                "https://clyp.it/rt5rybyc"));

        cases.add(new Case("reverbnation", ReverbnationAudioSourceManager::new,
                "https://www.reverbnation.com/paredaodofunkoficial/song/24395949-clean-bandit-feat-jess-glynne-rather"));

        cases.add(new Case("bandlab", BandlabAudioSourceManager::new,
                "https://www.bandlab.com/track/8557ba3e-be23-f011-8b3d-000d3aa44618?revId=8457ba3e-be23-f011-8b3d-000d3aa44618"));

        cases.add(new Case("rumble", RumbleAudioSourceManager::new,
                "https://rumble.com/v6rdhsm-golden-by-harry-styles.html?e9s=src_v1_s%2Csrc_v1_s_o&sci=a05a85b2-dcf1-4966-ac6f-e315943fbf44"));

        System.out.println("| Source | Status | Details |");
        System.out.println("|---|---:|---|");

        for (Case testCase : cases) {
            if (testCase.identifier == null || testCase.identifier.isBlank()) {
                System.out.printf("| %s | SKIP | sem URL de teste confiável definida |%n", testCase.name);
                continue;
            }

            Result result = runCase(testCase);
            System.out.printf("| %s | %s | %s |%n",
                    testCase.name,
                    result.status,
                    sanitize(result.details)
            );
        }
    }

    private static Result runCase(Case testCase) {
        AudioPlayerManager manager = new DefaultAudioPlayerManager();

        try {
            manager.setHttpRequestConfigurator(config -> RequestConfig.copy(config)
                    .setConnectTimeout(10_000)
                    .setSocketTimeout(20_000)
                    .setConnectionRequestTimeout(10_000)
                    .build());

            manager.registerSourceManager(testCase.factory.get());

            Handler handler = new Handler();

            Future<Void> future = manager.loadItem(testCase.identifier, handler);
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (handler.result == null) {
                return new Result("FAIL", "handler não retornou resultado");
            }

            return handler.result;
        } catch (Exception e) {
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

    private static class Handler implements AudioLoadResultHandler {
        private Result result;

        @Override
        public void trackLoaded(AudioTrack track) {
            result = new Result("OK", "track: " + track.getInfo().title);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            result = new Result("OK", "playlist: " + playlist.getName() + " / tracks=" + playlist.getTracks().size());
        }

        @Override
        public void noMatches() {
            result = new Result("NO_MATCH", "nenhum resultado encontrado");
        }

        @Override
        public void loadFailed(FriendlyException exception) {
            result = new Result("FAIL", exception.severity + ": " + exception.getMessage());
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
        private final Supplier<AudioSourceManager> factory;
        private final String identifier;

        private Case(String name, Supplier<AudioSourceManager> factory, String identifier) {
            this.name = name;
            this.factory = factory;
            this.identifier = identifier;
        }
    }
}