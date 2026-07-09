package com.sedmelluq.discord.lavaplayer.container.playlists;

import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream;
import com.sedmelluq.discord.lavaplayer.container.mpegts.PesPacketInputStream;
import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamSegmentUrlProvider;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream.ADTS_ELEMENTARY_STREAM;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

/**
 * Audio track for HLS playlists.
 *
 * <p>
 * The old implementation forced every HLS stream to be:
 * HLS -> MPEG-TS segments -> ADTS/AAC.
 * </p>
 *
 * <p>
 * That works for many streams, but some audio-only HLS streams use direct ADTS/AAC segments.
 * This implementation detects the first joined segment format and chooses the correct processing path.
 * </p>
 */
public class HlsStreamTrack extends M3uStreamAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(HlsStreamTrack.class);

    private static final int DETECTION_MARK_LIMIT = 4096;
    private static final int DETECTION_SAMPLE_SIZE = 512;

    private final HlsStreamSegmentUrlProvider segmentUrlProvider;
    private final HttpInterfaceManager httpInterfaceManager;

    /**
     * @param trackInfo Track info
     * @param streamUrl HLS master or media playlist URL
     * @param httpInterfaceManager HTTP interface manager
     * @param isInnerUrl Whether {@code streamUrl} already points to the media playlist
     */
    public HlsStreamTrack(AudioTrackInfo trackInfo, String streamUrl, HttpInterfaceManager httpInterfaceManager, boolean isInnerUrl) {
        super(trackInfo);

        segmentUrlProvider = isInnerUrl
                ? new HlsStreamSegmentUrlProvider(null, streamUrl)
                : new HlsStreamSegmentUrlProvider(streamUrl, null);

        this.httpInterfaceManager = httpInterfaceManager;
    }

    @Override
    protected M3uStreamSegmentUrlProvider getSegmentUrlProvider() {
        return segmentUrlProvider;
    }

    @Override
    protected HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    @Override
    protected void processJoinedStream(LocalAudioTrackExecutor localExecutor, InputStream stream) throws Exception {
        BufferedInputStream bufferedStream = stream instanceof BufferedInputStream
                ? (BufferedInputStream) stream
                : new BufferedInputStream(stream, DETECTION_MARK_LIMIT);

        SegmentFormat segmentFormat = detectSegmentFormat(bufferedStream);

        if (segmentFormat == SegmentFormat.ADTS) {
            log.debug("Detected HLS stream {} as direct ADTS/AAC segments.", getIdentifier());
            processDelegate(new AdtsAudioTrack(trackInfo, bufferedStream), localExecutor);
            return;
        }

        if (segmentFormat == SegmentFormat.MPEG_TS || segmentFormat == SegmentFormat.UNKNOWN) {
            if (segmentFormat == SegmentFormat.UNKNOWN) {
                log.debug("Could not detect HLS segment format for {}, falling back to MPEG-TS ADTS processing.", getIdentifier());
            } else {
                log.debug("Detected HLS stream {} as MPEG-TS ADTS segments.", getIdentifier());
            }

            MpegTsElementaryInputStream elementaryInputStream =
                    new MpegTsElementaryInputStream(bufferedStream, ADTS_ELEMENTARY_STREAM);

            PesPacketInputStream pesPacketInputStream =
                    new PesPacketInputStream(elementaryInputStream);

            processDelegate(new AdtsAudioTrack(trackInfo, pesPacketInputStream), localExecutor);
            return;
        }

        throw new FriendlyException(
                "This HLS stream uses fragmented MP4/CMAF segments, which are not supported by this lavaplayer build.",
                COMMON,
                null
        );
    }

    private static SegmentFormat detectSegmentFormat(BufferedInputStream inputStream) throws IOException {
        inputStream.mark(DETECTION_MARK_LIMIT);

        byte[] sample = new byte[DETECTION_SAMPLE_SIZE];
        int length;

        try {
            length = inputStream.read(sample);
        } finally {
            inputStream.reset();
        }

        if (length <= 0) {
            return SegmentFormat.UNKNOWN;
        }

        if (looksLikeMpegTs(sample, 0, length)) {
            return SegmentFormat.MPEG_TS;
        }

        if (looksLikeAdts(sample, 0, length)) {
            return SegmentFormat.ADTS;
        }

        if (startsWithId3(sample, length)) {
            /*
             * Some HLS ADTS/AAC streams have ID3 metadata before the first AAC frame.
             * AdtsStreamReader already scans until it finds the ADTS sync word, so direct ADTS processing is still correct.
             */
            return SegmentFormat.ADTS;
        }

        if (looksLikeFragmentedMp4(sample, 0, length)) {
            return SegmentFormat.FRAGMENTED_MP4;
        }

        return SegmentFormat.UNKNOWN;
    }

    private static boolean looksLikeMpegTs(byte[] sample, int offset, int length) {
        if (length - offset < 1 || unsigned(sample[offset]) != 0x47) {
            return false;
        }

        /*
         * MPEG-TS packets usually have 188 bytes.
         * If we have enough bytes, validate more than one sync byte to avoid false positives.
         */
        if (length - offset > 376) {
            return unsigned(sample[offset + 188]) == 0x47
                    && unsigned(sample[offset + 376]) == 0x47;
        }

        if (length - offset > 188) {
            return unsigned(sample[offset + 188]) == 0x47;
        }

        return true;
    }

    private static boolean looksLikeAdts(byte[] sample, int offset, int length) {
        if (length - offset < 2) {
            return false;
        }

        int first = unsigned(sample[offset]);
        int second = unsigned(sample[offset + 1]);

        return first == 0xFF && (second & 0xF0) == 0xF0;
    }

    private static boolean startsWithId3(byte[] sample, int length) {
        return length >= 3
                && sample[0] == 'I'
                && sample[1] == 'D'
                && sample[2] == '3';
    }

    private static boolean looksLikeFragmentedMp4(byte[] sample, int offset, int length) {
        if (length - offset < 8) {
            return false;
        }

        return matchesAscii(sample, offset + 4, length, "ftyp")
                || matchesAscii(sample, offset + 4, length, "moof")
                || matchesAscii(sample, offset + 4, length, "styp");
    }

    private static boolean matchesAscii(byte[] sample, int offset, int length, String value) {
        if (offset < 0 || length - offset < value.length()) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {
            if (sample[offset + i] != value.charAt(i)) {
                return false;
            }
        }

        return true;
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }

    private enum SegmentFormat {
        MPEG_TS,
        ADTS,
        FRAGMENTED_MP4,
        UNKNOWN
    }
}