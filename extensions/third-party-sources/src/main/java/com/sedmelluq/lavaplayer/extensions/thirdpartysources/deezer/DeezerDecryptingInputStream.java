package com.sedmelluq.lavaplayer.extensions.thirdpartysources.deezer;

import com.sedmelluq.discord.lavaplayer.tools.io.ByteBufferInputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class DeezerDecryptingInputStream extends InputStream {
    private static final byte[] IV = new byte[]{0, 1, 2, 3, 4, 5, 6, 7};

    private final InputStream input;
    private final ByteBuffer buffer;
    private final InputStream bufferInput;
    private final byte[] keyMaterial;

    private long blockIndex;
    private boolean hasBufferedData;

    public DeezerDecryptingInputStream(
            InputStream input,
            byte[] keyMaterial,
            long startBlockIndex,
            long bytesToDiscard
    ) throws IOException {
        this.input = new BufferedInputStream(input);
        this.buffer = ByteBuffer.allocate(DeezerSeekableInputStream.BLOCK_SIZE);
        this.bufferInput = new ByteBufferInputStream(this.buffer);
        this.keyMaterial = keyMaterial;

        this.blockIndex = startBlockIndex;
        this.hasBufferedData = false;

        if (bytesToDiscard > 0) {
            discardDecryptedBytes(bytesToDiscard);
        }
    }

    @Override
    public int read() throws IOException {
        if (!hasBufferedData || bufferInput.available() <= 0) {
            if (!fillNextBlock()) {
                return -1;
            }
        }

        return bufferInput.read();
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }

        if (offset < 0 || length < 0 || length > bytes.length - offset) {
            throw new IndexOutOfBoundsException();
        }

        if (length == 0) {
            return 0;
        }

        int totalRead = 0;

        while (length > 0) {
            if (!hasBufferedData || bufferInput.available() <= 0) {
                if (!fillNextBlock()) {
                    return totalRead > 0 ? totalRead : -1;
                }
            }

            int available = bufferInput.available();
            int toRead = Math.min(length, available);

            int read = bufferInput.read(bytes, offset, toRead);

            if (read < 0) {
                return totalRead > 0 ? totalRead : -1;
            }

            totalRead += read;
            offset += read;
            length -= read;
        }

        return totalRead;
    }

    @Override
    public long skip(long amount) throws IOException {
        if (amount <= 0) {
            return 0;
        }

        byte[] discardBuffer = new byte[(int) Math.min(8192, amount)];
        long remaining = amount;

        while (remaining > 0) {
            int read = read(
                    discardBuffer,
                    0,
                    (int) Math.min(discardBuffer.length, remaining)
            );

            if (read < 0) {
                break;
            }

            remaining -= read;
        }

        return amount - remaining;
    }

    @Override
    public int available() throws IOException {
        return bufferInput.available();
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    private boolean fillNextBlock() throws IOException {
        byte[] chunk = input.readNBytes(DeezerSeekableInputStream.BLOCK_SIZE);

        if (chunk.length == 0) {
            return false;
        }

        buffer.clear();
        hasBufferedData = true;

        if (blockIndex % 3 > 0 || chunk.length < DeezerSeekableInputStream.BLOCK_SIZE) {
            buffer.put(chunk);
        } else {
            buffer.put(decryptBlock(chunk));
        }

        blockIndex++;
        buffer.flip();

        return true;
    }

    private byte[] decryptBlock(byte[] chunk) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("Blowfish/CBC/NoPadding");

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyMaterial, "Blowfish"),
                    new IvParameterSpec(IV)
            );

            return cipher.doFinal(chunk);
        } catch (
                NoSuchAlgorithmException |
                NoSuchPaddingException |
                InvalidKeyException |
                InvalidAlgorithmParameterException |
                IllegalBlockSizeException |
                BadPaddingException exception
        ) {
            throw new IOException("Failed to decrypt Deezer block.", exception);
        }
    }

    private void discardDecryptedBytes(long bytesToDiscard) throws IOException {
        byte[] discardBuffer = new byte[8192];

        long remaining = bytesToDiscard;

        while (remaining > 0) {
            int read = read(
                    discardBuffer,
                    0,
                    (int) Math.min(discardBuffer.length, remaining)
            );

            if (read < 0) {
                throw new IOException("Unexpected EOF while discarding Deezer stream.");
            }

            remaining -= read;
        }
    }
}
