package com.sedmelluq.discord.lavaplayer.container.latm;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerProbe;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult.supportedFormat;

public class LatmContainerProbe implements MediaContainerProbe {
  private static final Logger log = LoggerFactory.getLogger(LatmContainerProbe.class);

  @Override
  public String getName() {
    return "latm";
  }

  @Override
  public boolean matchesHints(MediaContainerHints hints) {
    boolean validMimeType = hints.mimeType != null && (
        "audio/mp4a-latm".equalsIgnoreCase(hints.mimeType)
            || "audio/latm".equalsIgnoreCase(hints.mimeType)
            || "audio/loas".equalsIgnoreCase(hints.mimeType)
    );

    boolean validExtension = hints.fileExtension != null && (
        "latm".equalsIgnoreCase(hints.fileExtension)
            || "loas".equalsIgnoreCase(hints.fileExtension)
    );

    return validMimeType || validExtension;
  }

  @Override
  public MediaContainerDetectionResult probe(AudioReference reference, SeekableInputStream inputStream) throws IOException {
    if (!findLoasSync(inputStream, MediaContainerDetection.STREAM_SCAN_DISTANCE)) {
      return null;
    }

    log.debug("Track {} is a LATM/LOAS AAC stream.", reference.identifier);
    return supportedFormat(this, null, AudioTrackInfoBuilder.create(reference, inputStream).build());
  }

  @Override
  public AudioTrack createTrack(String parameters, AudioTrackInfo trackInfo, SeekableInputStream inputStream) {
    return new LatmAudioTrack(trackInfo, inputStream);
  }

  private static boolean findLoasSync(SeekableInputStream inputStream, int maximumDistance) throws IOException {
    long startPosition = inputStream.getPosition();

    try {
      int previous = -1;

      for (int i = 0; i < maximumDistance; i++) {
        int current = inputStream.read();

        if (current == -1) {
          return false;
        }

        if (previous == 0x56 && (current & 0xE0) == 0xE0) {
          return true;
        }

        previous = current;
      }

      return false;
    } finally {
      inputStream.seek(startPosition);
    }
  }
}
