package com.sedmelluq.discord.lavaplayer.container;

import com.sedmelluq.discord.lavaplayer.container.adts.AdtsContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.flac.FlacContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.latm.LatmContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.matroska.MatroskaContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3ContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.mpegts.MpegAdtsContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.mpegts.MpegLatmContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.ogg.OggContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.playlists.M3uPlaylistContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.playlists.PlainPlaylistContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.playlists.PlsPlaylistContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.wav.WavContainerProbe;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists currently supported containers and their probes.
 */
public enum MediaContainer {
  WAV(new WavContainerProbe()),
  MKV(new MatroskaContainerProbe()),
  MP4(new MpegContainerProbe()),
  FLAC(new FlacContainerProbe()),
  OGG(new OggContainerProbe()),
  M3U(new M3uPlaylistContainerProbe()),
  PLS(new PlsPlaylistContainerProbe()),
  PLAIN(new PlainPlaylistContainerProbe()),
  MP3(new Mp3ContainerProbe()),
  ADTS(new AdtsContainerProbe()),
  LATM(new LatmContainerProbe()),
  MPEGADTS(new MpegAdtsContainerProbe()),
  MPEGLATM(new MpegLatmContainerProbe());

  public final MediaContainerProbe probe;

  MediaContainer(MediaContainerProbe probe) {
    this.probe = probe;
  }

  public static List<MediaContainerProbe> asList() {
    List<MediaContainerProbe> probes = new ArrayList<>();

    for (MediaContainer container : MediaContainer.class.getEnumConstants()) {
      probes.add(container.probe);
    }

    return probes;
  }
}
