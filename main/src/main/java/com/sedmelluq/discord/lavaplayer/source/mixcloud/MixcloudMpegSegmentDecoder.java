package com.sedmelluq.discord.lavaplayer.source.mixcloud;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAacTrackConsumer;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegFileLoader;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegTrackConsumer;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegTrackInfo;
import com.sedmelluq.discord.lavaplayer.container.mpeg.reader.MpegFileTrackProvider;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.FAULT;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class MixcloudMpegSegmentDecoder implements MixcloudSegmentDecoder {
    private static final Logger log = LoggerFactory.getLogger(MixcloudMpegSegmentDecoder.class);

    private final Supplier<SeekableInputStream> nextStreamProvider;

    public MixcloudMpegSegmentDecoder(Supplier<SeekableInputStream> nextStreamProvider) {
        this.nextStreamProvider = nextStreamProvider;
    }

    @Override
    public void prepareStream(boolean beginning) {
        // Nothing to do.
    }

    @Override
    public void resetStream() {
        // Nothing to do.
    }

    @Override
    public void playStream(
        AudioProcessingContext context,
        long startPosition,
        long desiredPosition
    ) throws InterruptedException, IOException {
        try (SeekableInputStream stream = nextStreamProvider.get()) {
            MpegFileLoader file = new MpegFileLoader(stream);
            MpegTrackConsumer trackConsumer = loadAudioTrack(file, context);
            MpegFileTrackProvider fileReader = file.loadReader(trackConsumer);
            try {
                file.parseHeaders();
                fileReader.provideFrames();
            } finally {
                trackConsumer.close();
            }
        }
    }

    @Override
    public void close() {
        // Nothing to do.
    }

    private MpegTrackConsumer loadAudioTrack(MpegFileLoader file, AudioProcessingContext context) {
        MpegTrackConsumer trackConsumer = null;
        boolean success = false;
    
        try {
            trackConsumer = selectAudioTrack(file.getTrackList(), context);
    
            if (trackConsumer == null) {
                StringBuilder error = new StringBuilder();
                error.append("The audio codec used in the track is not supported, options:\n");
                file.getTrackList().forEach(track -> error.append(track.handler).append("|").append(track.codecName).append("\n"));
                throw new FriendlyException(error.toString(), SUSPICIOUS, null);
            } else {
                log.debug("Starting to play track with codec {}", trackConsumer.getTrack().codecName);
            }
    
            trackConsumer.initialise();
            success = true;
            return trackConsumer;
        } catch (Exception e) {
            throw ExceptionTools.wrapUnfriendlyExceptions("Something went wrong when loading an MP4 format track.", FAULT, e);
        } finally {
            if (!success && trackConsumer != null) {
                trackConsumer.close();
            }
        }
    }

    private MpegTrackConsumer selectAudioTrack(List<MpegTrackInfo> tracks, AudioProcessingContext context) {
        for (MpegTrackInfo track : tracks) {
            if ("soun".equals(track.handler) && "mp4a".equals(track.codecName)) {
                return new MpegAacTrackConsumer(context, track);
            }
        }
        return null;
    }
}