package com.sedmelluq.discord.lavaplayer.track;

import org.jetbrains.annotations.Nullable;

/**
 * Meta info for an audio track
 */
public class AudioTrackInfo {
  /**
   * Track title
   */
  public final String title;
  /**
   * Track author, if known
   */
  public final String author;
  /**
   * Length of the track in milliseconds, UnitConstants.DURATION_MS_UNKNOWN for streams
   */
  public final long length;
  /**
   * Audio source specific track identifier
   */
  public final String identifier;
  /**
   * True if this track is a stream
   */
  public final boolean isStream;
  /**
   * URL of the track, or local path to the file
   */
  public final String uri;
  /**
   * URL to thumbnail of the track
   */
  public final String artworkUrl;
  /**
   * True if this track is considered explicit.
   */
  @Nullable
  public Boolean explicit;

  /**
   * International Standard Recording Code.
   */
  @Nullable
  public String isrc;


  /**
   * @param title Track title
   * @param author Track author, if known
   * @param length Length of the track in milliseconds
   * @param identifier Audio source specific track identifier
   * @param isStream True if this track is a stream
   * @param uri URL of the track or path to its file
   * @param artworkUrl Thumbnail of the track
   * @param explicit True if this track is considered explicit
   * @param isrc International Standard Recording Code
   */
  public AudioTrackInfo(String title, String author, long length, String identifier, boolean isStream, String uri, String artworkUrl, @Nullable Boolean explicit, @Nullable String isrc) {
    this.title = title;
    this.author = author;
    this.length = length;
    this.identifier = identifier;
    this.isStream = isStream;
    this.uri = uri;
    this.artworkUrl = artworkUrl;
    this.explicit = explicit;
    this.isrc = isrc;
  }

  /**
   * @param title Track title
   * @param author Track author, if known
   * @param length Length of the track in milliseconds
   * @param identifier Audio source specific track identifier
   * @param isStream True if this track is a stream
   * @param uri URL of the track or path to its file
   * @param artworkUrl URL to thumbnail of the track
   */
  public AudioTrackInfo(String title, String author, long length, String identifier, boolean isStream, String uri, String artworkUrl) {
    this(title, author, length, identifier, isStream, uri, artworkUrl, null, null);
  }

  /**
   * @param title Track title
   * @param author Track author, if known
   * @param length Length of the track in milliseconds
   * @param identifier Audio source specific track identifier
   * @param isStream True if this track is a stream
   * @param uri URL of the track or path to its file
   * @param artworkUrl URL to thumbnail of the track
   * @param isrc International Standard Recording Code
   */
  public AudioTrackInfo(String title, String author, long length, String identifier, boolean isStream, String uri, String artworkUrl, String isrc) {
    this(title, author, length, identifier, isStream, uri, artworkUrl, null, isrc);
  }

  /**
   * @param title Track title
   * @param author Track author, if known
   * @param length Length of the track in milliseconds
   * @param identifier Audio source specific track identifier
   * @param isStream True if this track is a stream
   * @param uri URL of the track or path to its file
   * @param artworkUrl URL to thumbnail of the track
   * @param explicit True if this track is considered explicit
   */
  public AudioTrackInfo(String title, String author, long length, String identifier, boolean isStream, String uri, String artworkUrl, boolean explicit) {
    this(title, author, length, identifier, isStream, uri, artworkUrl, explicit, null);
  }

  /**
   * @param title Track title
   * @param author Track author, if known
   * @param length Length of the track in milliseconds
   * @param identifier Audio source specific track identifier
   * @param isStream True if this track is a stream
   * @param uri URL of the track or path to its file
   */
  public AudioTrackInfo(String title, String author, long length, String identifier, boolean isStream, String uri) {
    this(title, author, length, identifier, isStream, uri, System.getProperty("defaultArtworkUrl", null), null, null);
  }
}