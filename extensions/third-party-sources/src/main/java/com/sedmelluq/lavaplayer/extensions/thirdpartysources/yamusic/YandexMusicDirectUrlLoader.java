package com.sedmelluq.lavaplayer.extensions.thirdpartysources.yamusic;

public interface YandexMusicDirectUrlLoader extends YandexMusicApiLoader {
  String getDirectUrl(String trackId, String codec);
}
