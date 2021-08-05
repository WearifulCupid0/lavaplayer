package com.sedmelluq.discord.lavaplayer.track;

import java.util.List;

/**
 * The basic implementation of AudioPlaylist
 */
public class BasicAudioPlaylist implements AudioPlaylist {
  private final String name;
  private final String creator;
  private final String image;
  private final String uri;
  private final String type;
  private final List<AudioTrack> tracks;
  private final AudioTrack selectedTrack;
  private final boolean isSearchResult;

  /**
   * @param name Name of the playlist
   * @param creator Creator of the playlist
   * @param image Image of the playlist
   * @param uri Uri of the playlist
   * @param type Type of the playlist
   * @param tracks List of tracks in the playlist
   * @param selectedTrack Track that is explicitly selected
   * @param isSearchResult True if the playlist was created from search results
   */
  public BasicAudioPlaylist(String name, String creator, String image, String uri, String type, List<AudioTrack> tracks, AudioTrack selectedTrack, boolean isSearchResult) {
    this.name = name;
    this.creator = creator;
    this.image = image;
    this.uri = uri;
    this.type = type;
    this.tracks = tracks;
    this.selectedTrack = selectedTrack;
    this.isSearchResult = isSearchResult;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getCreator() {
    return creator;
  }

  @Override
  public String getImage() {
    return image;
  }

  @Override
  public String getUri() {
    return uri;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public List<AudioTrack> getTracks() {
    return tracks;
  }

  @Override
  public AudioTrack getSelectedTrack() {
    return selectedTrack;
  }

  @Override
  public boolean isSearchResult() {
    return isSearchResult;
  }
}
