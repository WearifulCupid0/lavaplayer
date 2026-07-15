package com.sedmelluq.discord.lavaplayer.container.playlists.cmaf;

import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class FragmentedMp4AdtsInputStream extends InputStream {
  private final HttpInterface httpInterface;
  private final HlsCmafSegmentProvider segmentProvider;

  private ByteArrayInputStream currentBuffer = new ByteArrayInputStream(new byte[0]);
  private AacAudioConfig audioConfig;
  private String currentInitKey;
  private boolean finished;

  public FragmentedMp4AdtsInputStream(HttpInterface httpInterface, HlsCmafSegmentProvider segmentProvider) {
    this.httpInterface = httpInterface;
    this.segmentProvider = segmentProvider;
  }

  @Override
  public int read() throws IOException {
    byte[] one = new byte[1];
    int result = read(one, 0, 1);
    return result < 0 ? -1 : one[0] & 0xFF;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    }

    while (currentBuffer.available() <= 0) {
      if (!fillBuffer()) {
        return -1;
      }
    }

    return currentBuffer.read(buffer, offset, length);
  }

  private boolean fillBuffer() throws IOException {
    if (finished) {
      return false;
    }

    while (true) {
      HlsCmafSegment segment;
      try {
        segment = segmentProvider.getNextSegment(httpInterface);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        InterruptedIOException interrupted = new InterruptedIOException("Interrupted while waiting for next CMAF HLS segment.");
        interrupted.initCause(e);
        throw interrupted;
      }

      if (segment == null) {
        finished = true;
        return false;
      }

      ensureInitSegment(segment);

      byte[] fragment = fetchBytes(segment.url, segment.byteRange);
      byte[] adtsBytes = FragmentedMp4Demuxer.demuxToAdts(fragment, audioConfig);

      if (adtsBytes.length == 0) {
        continue;
      }

      currentBuffer = new ByteArrayInputStream(adtsBytes);
      return true;
    }
  }

  private void ensureInitSegment(HlsCmafSegment segment) throws IOException {
    String initKey = segment.initUrl + "#" + rangeKey(segment.initByteRange);
    if (audioConfig != null && initKey.equals(currentInitKey)) {
      return;
    }

    byte[] initBytes = fetchBytes(segment.initUrl, segment.initByteRange);
    audioConfig = FragmentedMp4Demuxer.parseAacConfig(initBytes);
    currentInitKey = initKey;
  }

  private byte[] fetchBytes(String url, HlsByteRange byteRange) throws IOException {
    HttpGet request = new HttpGet(url);
    if (byteRange != null) {
      request.setHeader("Range", byteRange.toHttpRangeHeader());
    }

    return executeToByteArray(request, url);
  }

  private byte[] executeToByteArray(HttpUriRequest request, String url) throws IOException {
    CloseableHttpResponse response = null;
    boolean success = false;

    try {
      response = httpInterface.execute(request);
      int statusCode = response.getStatusLine().getStatusCode();

      if (!HttpClientTools.isSuccessWithContent(statusCode)) {
        throw new IOException("Invalid status code from CMAF HLS URL " + url + ": " + statusCode);
      }

      byte[] bytes = readAllBytes(response.getEntity().getContent());
      success = true;
      return bytes;
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new FriendlyException("Failed to fetch CMAF HLS data.", SUSPICIOUS, e);
    } finally {
      if (response != null) {
        if (!success) {
          ExceptionTools.closeWithWarnings(response);
        } else {
          response.close();
        }
      }
    }
  }

  private static byte[] readAllBytes(InputStream inputStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[16 * 1024];

    while (true) {
      int read = inputStream.read(buffer);
      if (read < 0) {
        break;
      }
      if (read > 0) {
        outputStream.write(buffer, 0, read);
      }
    }

    return outputStream.toByteArray();
  }

  private static String rangeKey(HlsByteRange byteRange) {
    if (byteRange == null) {
      return "full";
    }

    return byteRange.offset + ":" + byteRange.length;
  }
}
