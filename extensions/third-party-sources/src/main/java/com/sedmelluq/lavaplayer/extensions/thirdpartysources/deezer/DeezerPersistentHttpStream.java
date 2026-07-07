package com.sedmelluq.lavaplayer.extensions.thirdpartysources.deezer;

import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoProvider;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.List;

public class DeezerPersistentHttpStream extends SeekableInputStream {
    private static final Logger log = LoggerFactory.getLogger(DeezerPersistentHttpStream.class);

    public static final int BLOCK_SIZE = 2048;
    public static final long MAX_SKIP_DISTANCE = 512L * 1024L;

    private final HttpInterface httpInterface;
    private final URI contentUrl;
    private final byte[] keyMaterial;

    private long position;

    private CloseableHttpResponse currentResponse;
    private InputStream currentContent;
    private int lastStatusCode;

    public DeezerPersistentHttpStream(
            HttpInterface httpInterface,
            URI contentUrl,
            Long contentLength,
            byte[] keyMaterial
    ) {
        super(contentLength == null ? Units.CONTENT_LENGTH_UNKNOWN : contentLength, MAX_SKIP_DISTANCE);

        this.httpInterface = httpInterface;
        this.contentUrl = contentUrl;
        this.keyMaterial = keyMaterial;
        this.position = 0L;
    }

    public int checkStatusCode() throws IOException {
        connect(true);
        return lastStatusCode;
    }

    @Override
    public long getPosition() {
        return position;
    }

    public HttpResponse getCurrentResponse() {
        return currentResponse;
    }

    private void connect(boolean skipStatusCheck) throws IOException {
        if (currentResponse != null) {
            return;
        }

        IOException lastException = null;

        for (int attempt = 0; attempt < 2; attempt++) {
            boolean retryOnServerError = attempt == 0;

            try {
                if (attemptConnect(skipStatusCheck, retryOnServerError)) {
                    return;
                }
            } catch (IOException exception) {
                closeCurrentResponse();

                lastException = exception;

                if (!HttpClientTools.isRetriableNetworkException(exception) || attempt == 1) {
                    throw exception;
                }

                log.debug(
                        "Retriable exception while connecting Deezer stream {} at position {}. Retrying.",
                        contentUrl,
                        position,
                        exception
                );
            }
        }

        if (lastException != null) {
            throw lastException;
        }

        throw new IOException("Failed to connect Deezer stream.");
    }

    private boolean attemptConnect(boolean skipStatusCheck, boolean retryOnServerError) throws IOException {
        long alignedStart = alignToBlock(position);

        HttpGet request = new HttpGet(contentUrl);

        if (alignedStart > 0) {
            request.setHeader(HttpHeaders.RANGE, "bytes=" + alignedStart + "-");
        }

        currentResponse = httpInterface.execute(request);
        lastStatusCode = currentResponse.getStatusLine().getStatusCode();

        if (retryOnServerError && lastStatusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR) {
            closeCurrentResponse();
            return false;
        }

        if (!skipStatusCheck) {
            validateStatusCode(currentResponse);
        }

        if (currentResponse.getEntity() == null) {
            currentContent = new ByteArrayInputStream(new byte[0]);
            contentLength = 0;
            return true;
        }

        boolean acceptedRange = alignedStart > 0 && lastStatusCode == HttpStatus.SC_PARTIAL_CONTENT;

        long streamStart = acceptedRange ? alignedStart : 0;
        long bytesToDiscard = position - streamStart;
        long startBlockIndex = streamStart / BLOCK_SIZE;

        currentContent = new DeezerDecryptingInputStream(
                currentResponse.getEntity().getContent(),
                keyMaterial,
                startBlockIndex,
                bytesToDiscard
        );

        updateContentLength(currentResponse, acceptedRange);

        return true;
    }

    private static void validateStatusCode(HttpResponse response) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();

        boolean successWithContent =
                statusCode >= 200 &&
                        statusCode < 300 &&
                        statusCode != HttpStatus.SC_NO_CONTENT &&
                        statusCode != HttpStatus.SC_RESET_CONTENT;

        if (!successWithContent) {
            throw new IOException("Not success status code from Deezer CDN: " + statusCode);
        }
    }

    private static long alignToBlock(long position) {
        return (position / BLOCK_SIZE) * BLOCK_SIZE;
    }

    private void updateContentLength(HttpResponse response, boolean partialContent) {
        if (contentLength != Units.CONTENT_LENGTH_UNKNOWN) {
            return;
        }

        if (partialContent) {
            Header contentRange = response.getFirstHeader("Content-Range");

            if (contentRange != null) {
                String value = contentRange.getValue();
                int slashIndex = value.lastIndexOf('/');

                if (slashIndex >= 0 && slashIndex + 1 < value.length()) {
                    try {
                        contentLength = Long.parseLong(value.substring(slashIndex + 1));
                        return;
                    } catch (NumberFormatException ignored) {
                        // fallback abaixo
                    }
                }
            }

            return;
        }

        Header contentLengthHeader = response.getFirstHeader("Content-Length");

        if (contentLengthHeader != null) {
            try {
                contentLength = Long.parseLong(contentLengthHeader.getValue());
            } catch (NumberFormatException ignored) {
                contentLength = Units.CONTENT_LENGTH_UNKNOWN;
            }
        }
    }

    private void handleNetworkException(IOException exception, boolean attemptReconnect) throws IOException {
        if (!attemptReconnect || !HttpClientTools.isRetriableNetworkException(exception)) {
            throw exception;
        }

        closeCurrentResponse();

        log.debug(
                "Encountered retriable exception on Deezer url {} at position {}.",
                contentUrl,
                position,
                exception
        );
    }

    private int internalRead(boolean attemptReconnect) throws IOException {
        try {
            connect(false);

            int result = currentContent.read();

            if (result >= 0) {
                position++;
            }

            return result;
        } catch (IOException exception) {
            handleNetworkException(exception, attemptReconnect);
            return internalRead(false);
        }
    }

    @Override
    public int read() throws IOException {
        return internalRead(true);
    }

    private int internalRead(byte[] bytes, int offset, int length, boolean attemptReconnect) throws IOException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }

        if (offset < 0 || length < 0 || length > bytes.length - offset) {
            throw new IndexOutOfBoundsException();
        }

        if (length == 0) {
            return 0;
        }

        try {
            connect(false);

            int result = currentContent.read(bytes, offset, length);

            if (result > 0) {
                position += result;
            }

            return result;
        } catch (IOException exception) {
            handleNetworkException(exception, attemptReconnect);
            return internalRead(bytes, offset, length, false);
        }
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        return internalRead(bytes, offset, length, true);
    }

    private long internalSkip(long amount, boolean attemptReconnect) throws IOException {
        if (amount <= 0) {
            return 0;
        }

        try {
            connect(false);

            long result = currentContent.skip(amount);

            if (result > 0) {
                position += result;
            }

            return result;
        } catch (IOException exception) {
            handleNetworkException(exception, attemptReconnect);
            return internalSkip(amount, false);
        }
    }

    @Override
    public long skip(long amount) throws IOException {
        return internalSkip(amount, true);
    }

    private int internalAvailable(boolean attemptReconnect) throws IOException {
        try {
            connect(false);
            return currentContent.available();
        } catch (IOException exception) {
            handleNetworkException(exception, attemptReconnect);
            return internalAvailable(false);
        }
    }

    @Override
    public int available() throws IOException {
        return internalAvailable(true);
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void close() throws IOException {
        closeCurrentResponse();
    }

    public void releaseConnection() {
        try {
            closeCurrentResponse();
        } catch (IOException exception) {
            log.debug("Failed to release Deezer connection.", exception);
        }
    }

    private void closeCurrentResponse() throws IOException {
        IOException closeException = null;

        if (currentContent != null) {
            try {
                currentContent.close();
            } catch (IOException exception) {
                closeException = exception;
            }

            currentContent = null;
        }

        if (currentResponse != null) {
            try {
                currentResponse.close();
            } catch (IOException exception) {
                if (closeException == null) {
                    closeException = exception;
                }
            }

            currentResponse = null;
        }

        if (closeException != null) {
            throw closeException;
        }
    }

    @Override
    protected void seekHard(long position) throws IOException {
        if (position < 0) {
            throw new IOException("Cannot seek to negative position: " + position);
        }

        closeCurrentResponse();
        this.position = position;
    }

    @Override
    public boolean canSeekHard() {
        return true;
    }

    @Override
    public List<AudioTrackInfoProvider> getTrackInfoProviders() {
        return Collections.emptyList();
    }
}