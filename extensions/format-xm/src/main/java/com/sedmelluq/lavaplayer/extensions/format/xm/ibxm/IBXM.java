package com.sedmelluq.lavaplayer.extensions.format.xm.ibxm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 ProTracker, Scream Tracker 3, FastTracker 2 Replay (c)2015 mumart@gmail.com
 */
public class IBXM {
	public static final String		VERSION				= "a70dev (c)2015 mumart@gmail.com";

	private List<INoteListener>		noteListeners		= new ArrayList<>();

	private float					curt				= 0;
	private Module					module;
	private int[]					rampBuf;
	private Channel[]				channels;
	private int						sampleRate, interpolation;
	private int						seqPos, breakSeqPos, row, nextRow, tick;
	private int						speed, tempo, plCount, plChannel;
	private GlobalVol				globalVol;
	private Note					note;
	Map<Instrument, Integer>		instrumentLookup	= new HashMap<Instrument, Integer>();

	private Map<Integer, Integer>	lastKey				= new HashMap<>();

	private boolean					isEnd;

	/* Play the specified Module at the specified sampling rate. */
	public IBXM(final Module module, final int samplingRate) {
		this.module = module;
		for (int i = 0; i < module.instruments.length; i++) {
			this.instrumentLookup.put(module.instruments[i], i);
		}

		this.setSampleRate(samplingRate);
		this.interpolation = Channel.LINEAR;
		this.rampBuf = new int[128];
		this.channels = new Channel[module.numChannels];
		this.globalVol = new GlobalVol();
		this.note = new Note();
		this.setSequencePos(0);
	}

	/* Return the sampling rate of playback. */
	public int getSampleRate() {
		return this.sampleRate;
	}

	/*
	 * Set the sampling rate of playback. This can be used with Module.c2Rate to adjust the tempo and pitch.
	 */
	public void setSampleRate(final int rate) {
		if (rate < 8000 || rate > 128000) {
			throw new IllegalArgumentException("Unsupported sampling rate!");
		}
		this.sampleRate = rate;
	}

	/*
	 * Set the resampling quality to one of Channel.NEAREST, Channel.LINEAR, or Channel.SINC.
	 */
	public void setInterpolation(final int interpolation) {
		this.interpolation = interpolation;
	}

	/* Returns the length of the buffer required by getAudio(). */
	public int getMixBufferLength() {
		return (this.calculateTickLen(32, 128000) + 65) * 4;
	}

	/* Get the current row position. */
	public int getRow() {
		return this.row;
	}

	/* Get the current pattern position in the sequence. */
	public int getSequencePos() {
		return this.seqPos;
	}

	/* Set the pattern in the sequence to play. The tempo is reset to the default. */
	public void setSequencePos(int pos) {
		if (pos >= this.module.sequenceLength) {
			pos = 0;
		}
		this.breakSeqPos = pos;
		this.nextRow = 0;
		this.tick = 1;
		this.globalVol.volume = this.module.defaultGVol;
		this.speed = this.module.defaultSpeed > 0 ? this.module.defaultSpeed : 6;
		this.tempo = this.module.defaultTempo > 0 ? this.module.defaultTempo : 125;
		this.plCount = this.plChannel = -1;
		for (int idx = 0; idx < this.module.numChannels; idx++) {
			this.channels[idx] = new Channel(this, this.module, idx, this.globalVol);
		}
		for (int idx = 0; idx < 128; idx++) {
			this.rampBuf[idx] = 0;
		}
		this.tick();
	}

	/* Returns the song duration in samples at the current sampling rate. */
	public int calculateSongDuration() {
		int duration = 0;
		this.setSequencePos(0);
		boolean songEnd = false;
		while (!songEnd) {
			duration += this.calculateTickLen(this.tempo, this.sampleRate);
			songEnd = this.tick();
		}
		this.setSequencePos(0);
		return duration;
	}

	/*
	 * Seek to approximately the specified sample position. The actual sample position reached is returned.
	 */
	public int seek(final int samplePos) {
		this.setSequencePos(0);
		int currentPos = 0;
		int tickLen = this.calculateTickLen(this.tempo, this.sampleRate);
		while ((samplePos - currentPos) >= tickLen) {
			for (int idx = 0; idx < this.module.numChannels; idx++) {
				this.channels[idx].updateSampleIdx(tickLen * 2, this.sampleRate * 2);
			}
			currentPos += tickLen;
			this.tick();
			tickLen = this.calculateTickLen(this.tempo, this.sampleRate);
		}
		return currentPos;
	}

	/* Seek to the specified position and row in the sequence. */
	public void seekSequencePos(int sequencePos, int sequenceRow) {
		this.setSequencePos(0);
		if (sequencePos < 0 || sequencePos >= this.module.sequenceLength) {
			sequencePos = 0;
		}
		if (sequenceRow >= this.module.patterns[this.module.sequence[sequencePos]].numRows) {
			sequenceRow = 0;
		}
		while (this.seqPos < sequencePos || this.row < sequenceRow) {
			final int tickLen = this.calculateTickLen(this.tempo, this.sampleRate);
			for (int idx = 0; idx < this.module.numChannels; idx++) {
				this.channels[idx].updateSampleIdx(tickLen * 2, this.sampleRate * 2);
			}
			if (this.tick()) {
				/* Song end reached. */
				this.setSequencePos(sequencePos);
				return;
			}
		}
	}

	/*
	 * Generate audio. The number of samples placed into outputBuf is returned. The output buffer length must be at least that returned by getMixBufferLength(). A "sample" is a pair of 16-bit integer amplitudes, one for each of the stereo channels.
	 */

	public int getAudio(final int[] outputBuf) {
		final int tickLen = this.calculateTickLen(this.tempo, this.sampleRate);
		/* Clear output buffer. */
		for (int idx = 0, end = (tickLen + 65) * 4; idx < end; idx++) {
			outputBuf[idx] = 0;
		}
		/* Resample. */
		this.curt += tickLen / (float) this.sampleRate;
		for (int chanIdx = 0; chanIdx < this.module.numChannels; chanIdx++) {
			final Channel chan = this.channels[chanIdx];
			chan.resample(outputBuf, 0, (tickLen + 65) * 2, this.sampleRate * 2, this.interpolation);
			chan.updateSampleIdx(tickLen * 2, this.sampleRate * 2);
		}
		this.downsample(outputBuf, tickLen + 64);
		this.volumeRamp(outputBuf, tickLen);
		this.isEnd = this.tick();
		return tickLen;
	}

	public boolean isEnd() {
		return this.isEnd;
	}

	private int calculateTickLen(final int tempo, final int samplingRate) {
		return (samplingRate * 5) / (tempo * 2);
	}

	private void volumeRamp(final int[] mixBuf, final int tickLen) {
		final int rampRate = 256 * 2048 / this.sampleRate;
		for (int idx = 0, a1 = 0; a1 < 256; idx += 2, a1 += rampRate) {
			final int a2 = 256 - a1;
			mixBuf[idx] = (mixBuf[idx] * a1 + this.rampBuf[idx] * a2) >> 8;
			mixBuf[idx + 1] = (mixBuf[idx + 1] * a1 + this.rampBuf[idx + 1] * a2) >> 8;
		}
		System.arraycopy(mixBuf, tickLen * 2, this.rampBuf, 0, 128);
	}

	private void downsample(final int[] buf, final int count) {
		/* 2:1 downsampling with simple but effective anti-aliasing. Buf must contain count * 2 + 1 stereo samples. */
		final int outLen = count * 2;
		for (int inIdx = 0, outIdx = 0; outIdx < outLen; inIdx += 4, outIdx += 2) {
			buf[outIdx] = (buf[inIdx] >> 2) + (buf[inIdx + 2] >> 1) + (buf[inIdx + 4] >> 2);
			buf[outIdx + 1] = (buf[inIdx + 1] >> 2) + (buf[inIdx + 3] >> 1) + (buf[inIdx + 5] >> 2);
		}
	}

	private boolean tick() {
		boolean songEnd = false;
		if (--this.tick <= 0) {
			this.tick = this.speed;
			songEnd = this.row();
		} else {
			for (int idx = 0; idx < this.module.numChannels; idx++) {
				this.channels[idx].tick();
			}
		}
		return songEnd;
	}

	private boolean row() {
		boolean songEnd = false;
		if (this.breakSeqPos >= 0) {
			if (this.breakSeqPos >= this.module.sequenceLength) {
				this.breakSeqPos = this.nextRow = 0;
			}
			while (this.module.sequence[this.breakSeqPos] >= this.module.numPatterns) {
				this.breakSeqPos++;
				if (this.breakSeqPos >= this.module.sequenceLength) {
					this.breakSeqPos = this.nextRow = 0;
				}
			}
			if (this.breakSeqPos <= this.seqPos) {
				songEnd = true;
			}
			this.seqPos = this.breakSeqPos;
			for (int idx = 0; idx < this.module.numChannels; idx++) {
				this.channels[idx].plRow = 0;
			}
			this.breakSeqPos = -1;
		}
		final Pattern pattern = this.module.patterns[this.module.sequence[this.seqPos]];
		this.row = this.nextRow;
		if (this.row >= pattern.numRows) {
			this.row = 0;
		}
		this.nextRow = this.row + 1;
		if (this.nextRow >= pattern.numRows) {
			this.breakSeqPos = this.seqPos + 1;
			this.nextRow = 0;
		}
		final int noteIdx = this.row * this.module.numChannels;
		for (int chanIdx = 0; chanIdx < this.module.numChannels; chanIdx++) {
			final Channel channel = this.channels[chanIdx];
			pattern.getNote(noteIdx + chanIdx, this.note);
			if (this.note.effect == 0xE) {
				this.note.effect = 0x70 | (this.note.param >> 4);
				this.note.param &= 0xF;
			}
			if (this.note.effect == 0x93) {
				this.note.effect = 0xF0 | (this.note.param >> 4);
				this.note.param &= 0xF;
			}
			if (this.note.effect == 0 && this.note.param > 0) {
				this.note.effect = 0x8A;
			}
			channel.row(this.note);
			switch (this.note.effect) {
			case 0x81: /* Set Speed. */
				if (this.note.param > 0) {
					this.tick = this.speed = this.note.param;
				}
				break;
			case 0xB:
			case 0x82: /* Pattern Jump. */
				if (this.plCount < 0) {
					this.breakSeqPos = this.note.param;
					this.nextRow = 0;
				}
				break;
			case 0xD:
			case 0x83: /* Pattern Break. */
				if (this.plCount < 0) {
					this.breakSeqPos = this.seqPos + 1;
					this.nextRow = (this.note.param >> 4) * 10 + (this.note.param & 0xF);
				}
				break;
			case 0xF: /* Set Speed/Tempo. */
				if (this.note.param > 0) {
					if (this.note.param < 32) {
						this.tick = this.speed = this.note.param;
					} else {
						this.tempo = this.note.param;
					}
				}
				break;
			case 0x94: /* Set Tempo. */
				if (this.note.param > 32) {
					this.tempo = this.note.param;
				}
				break;
			case 0x76:
			case 0xFB: /* Pattern Loop. */
				if (this.note.param == 0) {
					channel.plRow = this.row;
				}
				if (channel.plRow < this.row) { /* Marker valid. Begin looping. */
					if (this.plCount < 0) { /* Not already looping, begin. */
						this.plCount = this.note.param;
						this.plChannel = chanIdx;
					}
					if (this.plChannel == chanIdx) { /* Next Loop. */
						if (this.plCount == 0) { /* Loop finished. */
							/* Invalidate current marker. */
							channel.plRow = this.row + 1;
						} else { /* Loop and cancel any breaks on this row. */
							this.nextRow = channel.plRow;
							this.breakSeqPos = -1;
						}
						this.plCount--;
					}
				}
				break;
			case 0x7E:
			case 0xFE: /* Pattern Delay. */
				this.tick = this.speed + this.speed * this.note.param;
				break;
			}

		}
		return songEnd;
	}

	public void onChannelnote(final int id, final int noteVol, final int noteKey, final int globalValume, final Instrument instrument, final int panning, final int freq) {
		this.lastKey.put(id, noteKey);
		int instrumentId = 0;
		if (this.instrumentLookup != null) {
			final Integer instrumentIdOrNull = this.instrumentLookup.get(instrument);
			if (instrumentIdOrNull != null) {
				instrumentId = instrumentIdOrNull;
			}
		}
		for (final INoteListener list : this.noteListeners) {
			list.onNote(this.curt, id, noteVol, noteKey, globalValume, instrumentId, panning, freq);
		}
	}

	public void addNoteListener(final INoteListener iNoteListener) {
		this.noteListeners.add(iNoteListener);
	}

	public void removeNoteListener(final INoteListener notelistener) {
		this.noteListeners.remove(notelistener);
	}

	public int determineTrackCount() {
		return this.module.numChannels;
	}

}
