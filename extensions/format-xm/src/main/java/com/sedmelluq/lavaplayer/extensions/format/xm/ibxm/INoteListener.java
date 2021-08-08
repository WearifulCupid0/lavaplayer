package com.sedmelluq.lavaplayer.extensions.format.xm.ibxm;

public interface INoteListener {
	public void onNote(float posInSec, int id, int noteVol, int noteKey, int globalVolume, int instrumentId, int panning, int freq);
}