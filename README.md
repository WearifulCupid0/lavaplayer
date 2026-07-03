# LavaPlayer Fork

A maintained personal fork of [`sedmelluq/lavaplayer`](https://github.com/sedmelluq/lavaplayer), focused on Discord music bots, modular source managers, and optional extensions such as Redis-based load result caching.

LavaPlayer loads audio from supported sources, decodes or passes through audio when possible, and provides Opus frames that can be sent to Discord voice connections.

This fork is public, but it is primarily maintained for the author's own bot and use cases. API compatibility with the original LavaPlayer or with other forks is not guaranteed.

## Important module note

The main `lavaplayer` artifact intentionally includes only the core player plus the two basic source managers:

- `HttpAudioSourceManager`
- `LocalAudioSourceManager`

External platform source managers such as SoundCloud, Bandcamp, Vimeo, Twitch, TuneIn, Odysee, Bilibili, Clyp, ReverbNation and similar sources are **not bundled in the main artifact**. To use those sources, add the `lavaplayer-source-module` artifact and register them from `NativeAudioSourceManagers`.

## Highlights

- Java 11 compatible.
- Gradle multi-module project.
- Core artifact kept lightweight: HTTP and local files only.
- External music/video/radio source managers available through a separate source module.
- Built-in support for common audio containers such as MP3, FLAC, WAV, MP4/M4A, Matroska/WebM, OGG, AAC, M3U and PLS.
- Optional Redis-based load result cache extension.
- JitPack-friendly build.

## Important notes

- YouTube support is not bundled in this fork.
- External source managers depend on third-party websites and can break when those websites change.
- This project is not affiliated with the original LavaPlayer project, Lavalink, Discord, or any supported audio platform.
- For public bots, always apply your own queue limits, request cooldowns, playlist limits and source restrictions.

## Installation with JitPack

This repository is intended to be consumed through [JitPack](https://jitpack.io).

Use a Git tag, commit hash, or branch snapshot as the version. Tags are recommended for stable builds.

### Gradle Kotlin DSL

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.WearifulCupid0.lavaplayer:lavaplayer:VERSION")
}
```

Example using the latest `master` snapshot:

```kotlin
dependencies {
    implementation("com.github.WearifulCupid0.lavaplayer:lavaplayer:master-SNAPSHOT")
}
```

### Gradle Groovy DSL

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.WearifulCupid0.lavaplayer:lavaplayer:VERSION'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.WearifulCupid0.lavaplayer</groupId>
        <artifactId>lavaplayer</artifactId>
        <version>VERSION</version>
    </dependency>
</dependencies>
```

## Available artifacts

Because this is a multi-module Gradle project, JitPack publishes modules using this format:

```text
com.github.WearifulCupid0.lavaplayer:MODULE:VERSION
```

Common modules:

| Artifact | Description |
|---|---|
| `lavaplayer` | Main LavaPlayer library. Includes core playback, HTTP source and local file source. |
| `lava-common` | Shared/common utilities used by the project. |
| `lavaplayer-source-module` | Optional external source managers such as SoundCloud, Bandcamp, Vimeo, Twitch, TuneIn, Odysee, Bilibili, Clyp and others. |
| `lavaplayer-stream-merger` | Stream merger module. |
| `lavaplayer-ext-format-xm` | Optional XM format extension. |
| `lavaplayer-ext-third-party-sources` | Optional third-party/resolver source extension. |
| `lavaplayer-ext-redis-cache` | Optional Redis cache extension. |

## Basic usage: core only

Create a single `AudioPlayerManager` for your application and register the built-in core sources.

```java
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;

AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

// In the main artifact, this registers HTTP and local-file support only.
AudioSourceManagers.registerRemoteSources(playerManager);
```

You can also register them explicitly:

```java
AudioSourceManagers.registerHttpSource(playerManager);
AudioSourceManagers.registerLocalSource(playerManager);
```

Then create one `AudioPlayer` per playback target, usually one per Discord guild:

```java
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;

AudioPlayer player = playerManager.createPlayer();
```

## Using external source managers

To use platform sources such as SoundCloud, Bandcamp, Vimeo, Twitch, TuneIn, Odysee, Bilibili, Clyp or ReverbNation, install the source module too.

```kotlin
dependencies {
    implementation("com.github.WearifulCupid0.lavaplayer:lavaplayer:VERSION")
    implementation("com.github.WearifulCupid0.lavaplayer:lavaplayer-source-module:VERSION")
}
```

Then register the native external sources:

```java
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.lavaplayer.source.NativeAudioSourceManagers;

AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

// Core sources from the main artifact.
AudioSourceManagers.registerHttpSource(playerManager);
AudioSourceManagers.registerLocalSource(playerManager);

// External source managers from lavaplayer-source-module.
NativeAudioSourceManagers.registerNativeSources(playerManager);
```

For public bots, consider registering only the source managers you actually want to expose instead of registering every available source.

## Loading tracks

```java
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

playerManager.loadItem(identifier, new AudioLoadResultHandler() {
    @Override
    public void trackLoaded(AudioTrack track) {
        player.playTrack(track);
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        if (!playlist.getTracks().isEmpty()) {
            player.playTrack(playlist.getTracks().get(0));
        }
    }

    @Override
    public void noMatches() {
        // Nothing was found for the identifier.
    }

    @Override
    public void loadFailed(FriendlyException exception) {
        // Loading failed.
    }
});
```

## JDA audio send handler

For Discord bots using JDA, prefer `MutableAudioFrame` to reduce allocations during playback.

```java
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;

public class LavaPlayerSendHandler implements AudioSendHandler {
    private final AudioPlayer audioPlayer;
    private final ByteBuffer buffer;
    private final MutableAudioFrame frame;

    public LavaPlayerSendHandler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
        this.buffer = ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize());
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(buffer);
    }

    @Override
    public boolean canProvide() {
        buffer.clear();
        return audioPlayer.provide(frame);
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        return buffer.flip();
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
```

## Redis cache extension

The Redis cache extension is intended to cache LavaPlayer load results, such as resolved tracks, playlists and short-lived `noMatches` results.

Add the extension:

```kotlin
dependencies {
    implementation("com.github.WearifulCupid0.lavaplayer:lavaplayer:VERSION")
    implementation("com.github.WearifulCupid0.lavaplayer:lavaplayer-ext-redis-cache:VERSION")
}
```

Example:

```java
import com.sedmelluq.lavaplayer.extensions.cache.CachingAudioPlayerManager;
import com.sedmelluq.lavaplayer.extensions.cache.RedisAudioLoadCache;
import com.sedmelluq.lavaplayer.extensions.cache.policy.CachePolicy;

CachingAudioPlayerManager playerManager = new CachingAudioPlayerManager();

playerManager.setAudioLoadCache(new RedisAudioLoadCache(
    "redis://localhost:6379",
    CachePolicy.defaultPolicy()
));
```

Recommended cache behavior:

- Cache normal resolved tracks.
- Cache playlists with a reasonable maximum size.
- Cache search results for a shorter period.
- Cache `noMatches` only for a very short period.
- Do not cache failed loads as permanent results.

## Source managers

The source manager layout is intentionally modular:

| Location | Source managers                                                                                                                                                                                |
|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `lavaplayer` main artifact | HTTP URLs and local files only.                                                                                                                                                                |
| `lavaplayer-source-module` | External native sources such as SoundCloud, Bandcamp, Vimeo, Twitch, TuneIn, Clyp, ReverbNation, Streamable, Odysee, Rumble, Bilibili, Jamendo, Mixcloud, iHeart, Nico, Ocremix and Soundgasm. |
| `lavaplayer-ext-third-party-sources` | Optional resolver/third-party sources that doesn't play audio natively.                                                                                                                        |

This means installing only `lavaplayer` gives you a small core. Install `lavaplayer-source-module` when you want the external native source managers.

## Performance recommendations for Discord bots

For public bots, do not rely only on LavaPlayer configuration. Add limits at the bot layer too.

Recommended bot-side limits:

- Maximum queue size per guild.
- Maximum playlist size.
- Command cooldowns.
- Maximum concurrent players.
- Maximum concurrent live streams or radio streams.
- Auto-disconnect when the voice channel is empty.
- Auto-cleanup for paused or inactive players.

Recommended LavaPlayer settings:

```java
playerManager.setFrameBufferDuration(1500);
playerManager.setItemLoaderThreadPoolSize(4);
playerManager.setUseSeekGhosting(false);
```

For lower allocation playback, use `MutableAudioFrame` in the Discord send handler.

## Building locally

Requirements:

- JDK 11+
- Gradle wrapper included in the repository

Build everything:

```bash
./gradlew clean build
```

On Windows:

```bat
gradlew.bat clean build
```

Publish to local Maven repository:

```bash
./gradlew publishToMavenLocal
```

## JitPack publishing

To publish a new version through JitPack:

```bash
git tag vX.Y.Z
git push origin vX.Y.Z
```

Then open the project on JitPack and build the new tag.

Consumers can use:

```kotlin
implementation("com.github.WearifulCupid0.lavaplayer:lavaplayer:vX.Y.Z")
```

For external sources:

```kotlin
implementation("com.github.WearifulCupid0.lavaplayer:lavaplayer-source-module:vX.Y.Z")
```

## License

This project follows the license of the original LavaPlayer project. See [`LICENSE`](LICENSE).
