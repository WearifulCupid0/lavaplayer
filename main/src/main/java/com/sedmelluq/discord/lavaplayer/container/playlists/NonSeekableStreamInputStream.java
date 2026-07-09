package com.sedmelluq.discord.lavaplayer.container.playlists;

import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper for live HLS segment streams.
 *
 * Mp3AudioTrack expects a SeekableInputStream, but HLS live segments are not hard-seekable.
 * This wrapper exposes a forward-only stream as a non-hard-seekable SeekableInputStream.
 */
public class NonSeekableStreamInputStream extends SeekableInputStream {
    private final InputStream delegate;
    private long position;

    private NonSeekableStreamInputStream(InputStream delegate) {
        super(Long.MAX_VALUE, Long.MAX_VALUE);

        this.delegate = delegate;
        this.position = 0L;
    }

    @Override
    public int read() throws IOException {
        int result = delegate.read();

        if (result >= 0) {
            position++;
        }

        return result;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = delegate.read(buffer, offset, length);

        if (read > 0) {
            position += read;
        }

        return read;
    }

    @Override
    public long skip(long distance) throws IOException {
        long skipped = delegate.skip(distance);

        if (skipped > 0) {
            position += skipped;
        }

        return skipped;
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    protected void seekHard(long position) throws IOException {
        if (position == this.position) {
            return;
        }

        throw new IOException("HLS MP3 streams are not seekable.");
    }

    @Override
    public boolean canSeekHard() {
        return false;
    }

    @Override
    public List<AudioTrackInfoProvider> getTrackInfoProviders() {
        return Collections.emptyList();
    }
}
