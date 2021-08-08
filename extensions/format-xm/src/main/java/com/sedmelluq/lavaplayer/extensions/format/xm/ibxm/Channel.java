package com.sedmelluq.lavaplayer.extensions.format.xm.ibxm;

public class Channel {
	public static final int			NEAREST		= 0, LINEAR = 1, SINC = 2;

	private static int[]			exp2Table	= { 32768, 32946, 33125, 33305, 33486, 33667, 33850, 34034, 34219, 34405, 34591, 34779, 34968, 35158, 35349, 35541, 35734, 35928, 36123, 36319, 36516, 36715, 36914, 37114, 37316, 37518, 37722, 37927,
		38133, 38340, 38548, 38757, 38968, 39180, 39392, 39606, 39821, 40037, 40255, 40473, 40693, 40914, 41136, 41360, 41584, 41810, 42037, 42265, 42495, 42726, 42958, 43191, 43425, 43661, 43898, 44137, 44376, 44617, 44859, 45103, 45348, 45594,
		45842, 46091, 46341, 46593, 46846, 47100, 47356, 47613, 47871, 48131, 48393, 48655, 48920, 49185, 49452, 49721, 49991, 50262, 50535, 50810, 51085, 51363, 51642, 51922, 52204, 52488, 52773, 53059, 53347, 53637, 53928, 54221, 54515, 54811,
		55109, 55408, 55709, 56012, 56316, 56622, 56929, 57238, 57549, 57861, 58176, 58491, 58809, 59128, 59449, 59772, 60097, 60423, 60751, 61081, 61413, 61746, 62081, 62419, 62757, 63098, 63441, 63785, 64132, 64480, 64830, 65182, 65536 };

	private static final short[]	sineTable	= { 0, 24, 49, 74, 97, 120, 141, 161, 180, 197, 212, 224, 235, 244, 250, 253, 255, 253, 250, 244, 235, 224, 212, 197, 180, 161, 141, 120, 97, 74, 49, 24 };

	private Module					module;
	private GlobalVol				globalVol;
	private Instrument				instrument;
	private Sample					sample;
	private boolean					keyOn;
	private int						noteKey, noteIns, noteVol, noteEffect, noteParam;
	private int						sampleOffset, sampleIdx, sampleFra, freq, ampl, pann;
	private int						volume, panning, fadeOutVol, volEnvTick, panEnvTick;
	private int						period, portaPeriod, retrigCount, fxCount, autoVibratoCount;
	private int						portaUpParam, portaDownParam, tonePortaParam, offsetParam;
	private int						finePortaUpParam, finePortaDownParam, extraFinePortaParam;
	private int						arpeggioParam, vslideParam, globalVslideParam, panningSlideParam;
	private int						fineVslideUpParam, fineVslideDownParam;
	private int						retrigVolume, retrigTicks, tremorOnTicks, tremorOffTicks;
	private int						vibratoType, vibratoPhase, vibratoSpeed, vibratoDepth;
	private int						tremoloType, tremoloPhase, tremoloSpeed, tremoloDepth;
	private int						tremoloAdd, vibratoAdd, arpeggioAdd;
	private int						id, randomSeed;
	public int						plRow;

	private IBXM					player;

	public Channel(final IBXM player, final Module module, final int id, final GlobalVol globalVol) {
		this.player = player;
		this.module = module;
		this.id = id;
		this.globalVol = globalVol;
		this.panning = module.defaultPanning[id];
		this.instrument = new Instrument();
		this.sample = this.instrument.samples[0];
		this.randomSeed = (id + 1) * 0xABCDEF;
	}

	public void resample(final int[] outBuf, final int offset, final int length, final int sampleRate, final int interpolation) {
		if (this.ampl <= 0) {
			return;
		}
		final int lAmpl = this.ampl * (255 - this.pann) >> 8;
		final int rAmpl = this.ampl * this.pann >> 8;
		final int step = (this.freq << (Sample.FP_SHIFT - 3)) / (sampleRate >> 3);
		switch (interpolation) {
		case NEAREST:
			this.sample.resampleNearest(this.sampleIdx, this.sampleFra, step, lAmpl, rAmpl, outBuf, offset, length);
			break;
		case LINEAR:
		default:
			this.sample.resampleLinear(this.sampleIdx, this.sampleFra, step, lAmpl, rAmpl, outBuf, offset, length);
			break;
		case SINC:
			this.sample.resampleSinc(this.sampleIdx, this.sampleFra, step, lAmpl, rAmpl, outBuf, offset, length);
			break;
		}
	}

	public void updateSampleIdx(final int length, final int sampleRate) {
		final int step = (this.freq << (Sample.FP_SHIFT - 3)) / (sampleRate >> 3);
		this.sampleFra += step * length;
		this.sampleIdx = this.sample.normaliseSampleIdx(this.sampleIdx + (this.sampleFra >> Sample.FP_SHIFT));
		this.sampleFra &= Sample.FP_MASK;
	}

	public void row(final Note note) {
		this.noteKey = note.key;
		this.noteIns = note.instrument;
		this.noteVol = note.volume;
		this.noteEffect = note.effect;
		this.noteParam = note.param;
		this.retrigCount++;
		this.vibratoAdd = this.tremoloAdd = this.arpeggioAdd = this.fxCount = 0;
		if (!((this.noteEffect == 0x7D || this.noteEffect == 0xFD) && this.noteParam > 0)) {
			/* Not note delay. */
			this.trigger();
		}
		switch (this.noteEffect) {
		case 0x01:
		case 0x86: /* Porta Up. */
			if (this.noteParam > 0) {
				this.portaUpParam = this.noteParam;
			}
			this.portamentoUp(this.portaUpParam);
			break;
		case 0x02:
		case 0x85: /* Porta Down. */
			if (this.noteParam > 0) {
				this.portaDownParam = this.noteParam;
			}
			this.portamentoDown(this.portaDownParam);
			break;
		case 0x03:
		case 0x87: /* Tone Porta. */
			if (this.noteParam > 0) {
				this.tonePortaParam = this.noteParam;
			}
			break;
		case 0x04:
		case 0x88: /* Vibrato. */
			if ((this.noteParam >> 4) > 0) {
				this.vibratoSpeed = this.noteParam >> 4;
			}
			if ((this.noteParam & 0xF) > 0) {
				this.vibratoDepth = this.noteParam & 0xF;
			}
			this.vibrato(false);
			break;
		case 0x05:
		case 0x8C: /* Tone Porta + Vol Slide. */
			if (this.noteParam > 0) {
				this.vslideParam = this.noteParam;
			}
			this.volumeSlide();
			break;
		case 0x06:
		case 0x8B: /* Vibrato + Vol Slide. */
			if (this.noteParam > 0) {
				this.vslideParam = this.noteParam;
			}
			this.vibrato(false);
			this.volumeSlide();
			break;
		case 0x07:
		case 0x92: /* Tremolo. */
			if ((this.noteParam >> 4) > 0) {
				this.tremoloSpeed = this.noteParam >> 4;
			}
			if ((this.noteParam & 0xF) > 0) {
				this.tremoloDepth = this.noteParam & 0xF;
			}
			this.tremolo();
			break;
		case 0x08: /* Set Panning. */
			this.panning = (this.noteParam < 128) ? (this.noteParam << 1) : 255;
			break;
		case 0x0A:
		case 0x84: /* Vol Slide. */
			if (this.noteParam > 0) {
				this.vslideParam = this.noteParam;
			}
			this.volumeSlide();
			break;
		case 0x0C: /* Set Volume. */
			this.volume = this.noteParam >= 64 ? 64 : this.noteParam & 0x3F;
			break;
		case 0x10:
		case 0x96: /* Set Global Volume. */
			this.globalVol.volume = this.noteParam >= 64 ? 64 : this.noteParam & 0x3F;
			break;
		case 0x11: /* Global Volume Slide. */
			if (this.noteParam > 0) {
				this.globalVslideParam = this.noteParam;
			}
			break;
		case 0x14: /* Key Off. */
			this.keyOn = false;
			break;
		case 0x15: /* Set Envelope Tick. */
			this.volEnvTick = this.panEnvTick = this.noteParam & 0xFF;
			break;
		case 0x19: /* Panning Slide. */
			if (this.noteParam > 0) {
				this.panningSlideParam = this.noteParam;
			}
			break;
		case 0x1B:
		case 0x91: /* Retrig + Vol Slide. */
			if ((this.noteParam >> 4) > 0) {
				this.retrigVolume = this.noteParam >> 4;
			}
			if ((this.noteParam & 0xF) > 0) {
				this.retrigTicks = this.noteParam & 0xF;
			}
			this.retrigVolSlide();
			break;
		case 0x1D:
		case 0x89: /* Tremor. */
			if ((this.noteParam >> 4) > 0) {
				this.tremorOnTicks = this.noteParam >> 4;
			}
			if ((this.noteParam & 0xF) > 0) {
				this.tremorOffTicks = this.noteParam & 0xF;
			}
			this.tremor();
			break;
		case 0x21: /* Extra Fine Porta. */
			if (this.noteParam > 0) {
				this.extraFinePortaParam = this.noteParam;
			}
			switch (this.extraFinePortaParam & 0xF0) {
			case 0x10:
				this.portamentoUp(0xE0 | (this.extraFinePortaParam & 0xF));
				break;
			case 0x20:
				this.portamentoDown(0xE0 | (this.extraFinePortaParam & 0xF));
				break;
			}
			break;
		case 0x71: /* Fine Porta Up. */
			if (this.noteParam > 0) {
				this.finePortaUpParam = this.noteParam;
			}
			this.portamentoUp(0xF0 | (this.finePortaUpParam & 0xF));
			break;
		case 0x72: /* Fine Porta Down. */
			if (this.noteParam > 0) {
				this.finePortaDownParam = this.noteParam;
			}
			this.portamentoDown(0xF0 | (this.finePortaDownParam & 0xF));
			break;
		case 0x74:
		case 0xF3: /* Set Vibrato Waveform. */
			if (this.noteParam < 8) {
				this.vibratoType = this.noteParam;
			}
			break;
		case 0x77:
		case 0xF4: /* Set Tremolo Waveform. */
			if (this.noteParam < 8) {
				this.tremoloType = this.noteParam;
			}
			break;
		case 0x7A: /* Fine Vol Slide Up. */
			if (this.noteParam > 0) {
				this.fineVslideUpParam = this.noteParam;
			}
			this.volume += this.fineVslideUpParam;
			if (this.volume > 64) {
				this.volume = 64;
			}
			break;
		case 0x7B: /* Fine Vol Slide Down. */
			if (this.noteParam > 0) {
				this.fineVslideDownParam = this.noteParam;
			}
			this.volume -= this.fineVslideDownParam;
			if (this.volume < 0) {
				this.volume = 0;
			}
			break;
		case 0x7C:
		case 0xFC: /* Note Cut. */
			if (this.noteParam <= 0) {
				this.volume = 0;
			}
			break;
		case 0x8A: /* Arpeggio. */
			if (this.noteParam > 0) {
				this.arpeggioParam = this.noteParam;
			}
			break;
		case 0x95: /* Fine Vibrato. */
			if ((this.noteParam >> 4) > 0) {
				this.vibratoSpeed = this.noteParam >> 4;
			}
			if ((this.noteParam & 0xF) > 0) {
				this.vibratoDepth = this.noteParam & 0xF;
			}
			this.vibrato(true);
			break;
		case 0xF8: /* Set Panning. */
			this.panning = this.noteParam * 17;
			break;
		}
		this.autoVibrato();
		this.calculateFrequency();
		this.calculateAmplitude();
		this.updateEnvelopes();
	}

	public void tick() {
		this.vibratoAdd = 0;
		this.fxCount++;
		this.retrigCount++;
		if (!(this.noteEffect == 0x7D && this.fxCount <= this.noteParam)) {
			switch (this.noteVol & 0xF0) {
			case 0x60: /* Vol Slide Down. */
				this.volume -= this.noteVol & 0xF;
				if (this.volume < 0) {
					this.volume = 0;
				}
				break;
			case 0x70: /* Vol Slide Up. */
				this.volume += this.noteVol & 0xF;
				if (this.volume > 64) {
					this.volume = 64;
				}
				break;
			case 0xB0: /* Vibrato. */
				this.vibratoPhase += this.vibratoSpeed;
				this.vibrato(false);
				break;
			case 0xD0: /* Pan Slide Left. */
				this.panning -= this.noteVol & 0xF;
				if (this.panning < 0) {
					this.panning = 0;
				}
				break;
			case 0xE0: /* Pan Slide Right. */
				this.panning += this.noteVol & 0xF;
				if (this.panning > 255) {
					this.panning = 255;
				}
				break;
			case 0xF0: /* Tone Porta. */
				this.tonePortamento();
				break;
			}
		}
		switch (this.noteEffect) {
		case 0x01:
		case 0x86: /* Porta Up. */
			this.portamentoUp(this.portaUpParam);
			break;
		case 0x02:
		case 0x85: /* Porta Down. */
			this.portamentoDown(this.portaDownParam);
			break;
		case 0x03:
		case 0x87: /* Tone Porta. */
			this.tonePortamento();
			break;
		case 0x04:
		case 0x88: /* Vibrato. */
			this.vibratoPhase += this.vibratoSpeed;
			this.vibrato(false);
			break;
		case 0x05:
		case 0x8C: /* Tone Porta + Vol Slide. */
			this.tonePortamento();
			this.volumeSlide();
			break;
		case 0x06:
		case 0x8B: /* Vibrato + Vol Slide. */
			this.vibratoPhase += this.vibratoSpeed;
			this.vibrato(false);
			this.volumeSlide();
			break;
		case 0x07:
		case 0x92: /* Tremolo. */
			this.tremoloPhase += this.tremoloSpeed;
			this.tremolo();
			break;
		case 0x0A:
		case 0x84: /* Vol Slide. */
			this.volumeSlide();
			break;
		case 0x11: /* Global Volume Slide. */
			this.globalVol.volume += (this.globalVslideParam >> 4) - (this.globalVslideParam & 0xF);
			if (this.globalVol.volume < 0) {
				this.globalVol.volume = 0;
			}
			if (this.globalVol.volume > 64) {
				this.globalVol.volume = 64;
			}
			break;
		case 0x19: /* Panning Slide. */
			this.panning += (this.panningSlideParam >> 4) - (this.panningSlideParam & 0xF);
			if (this.panning < 0) {
				this.panning = 0;
			}
			if (this.panning > 255) {
				this.panning = 255;
			}
			break;
		case 0x1B:
		case 0x91: /* Retrig + Vol Slide. */
			this.retrigVolSlide();
			break;
		case 0x1D:
		case 0x89: /* Tremor. */
			this.tremor();
			break;
		case 0x79: /* Retrig. */
			if (this.fxCount >= this.noteParam) {
				this.fxCount = 0;
				this.sampleIdx = this.sampleFra = 0;
			}
			break;
		case 0x7C:
		case 0xFC: /* Note Cut. */
			if (this.noteParam == this.fxCount) {
				this.volume = 0;
			}
			break;
		case 0x7D:
		case 0xFD: /* Note Delay. */
			if (this.noteParam == this.fxCount) {
				this.trigger();
			}
			break;
		case 0x8A: /* Arpeggio. */
			if (this.fxCount > 2) {
				this.fxCount = 0;
			}
			if (this.fxCount == 0) {
				this.arpeggioAdd = 0;
			}
			if (this.fxCount == 1) {
				this.arpeggioAdd = this.arpeggioParam >> 4;
			}
			if (this.fxCount == 2) {
				this.arpeggioAdd = this.arpeggioParam & 0xF;
			}
			break;
		case 0x95: /* Fine Vibrato. */
			this.vibratoPhase += this.vibratoSpeed;
			this.vibrato(true);
			break;
		}
		this.autoVibrato();
		this.calculateFrequency();
		this.calculateAmplitude();
		this.updateEnvelopes();
	}

	private void updateEnvelopes() {
		if (this.instrument.volumeEnvelope.enabled) {
			if (!this.keyOn) {
				this.fadeOutVol -= this.instrument.volumeFadeOut;
				if (this.fadeOutVol < 0) {
					this.fadeOutVol = 0;
				}
			}
			this.volEnvTick = this.instrument.volumeEnvelope.nextTick(this.volEnvTick, this.keyOn);
		}
		if (this.instrument.panningEnvelope.enabled) {
			this.panEnvTick = this.instrument.panningEnvelope.nextTick(this.panEnvTick, this.keyOn);
		}
	}

	private void autoVibrato() {
		int depth = this.instrument.vibratoDepth & 0x7F;
		if (depth > 0) {
			final int sweep = this.instrument.vibratoSweep & 0x7F;
			final int rate = this.instrument.vibratoRate & 0x7F;
			final int type = this.instrument.vibratoType;
			if (this.autoVibratoCount < sweep) {
				depth = depth * this.autoVibratoCount / sweep;
			}
			this.vibratoAdd += this.waveform(this.autoVibratoCount * rate >> 2, type + 4) * depth >> 8;
			this.autoVibratoCount++;
		}
	}

	private void volumeSlide() {
		final int up = this.vslideParam >> 4;
		final int down = this.vslideParam & 0xF;
		if (down == 0xF && up > 0) { /* Fine slide up. */
			if (this.fxCount == 0) {
				this.volume += up;
			}
		} else if (up == 0xF && down > 0) { /* Fine slide down. */
			if (this.fxCount == 0) {
				this.volume -= down;
			}
		} else if (this.fxCount > 0 || this.module.fastVolSlides) {
			this.volume += up - down;
		}
		if (this.volume > 64) {
			this.volume = 64;
		}
		if (this.volume < 0) {
			this.volume = 0;
		}
	}

	private void portamentoUp(final int param) {
		switch (param & 0xF0) {
		case 0xE0: /* Extra-fine porta. */
			if (this.fxCount == 0) {
				this.period -= param & 0xF;
			}
			break;
		case 0xF0: /* Fine porta. */
			if (this.fxCount == 0) {
				this.period -= (param & 0xF) << 2;
			}
			break;
		default:/* Normal porta. */
			if (this.fxCount > 0) {
				this.period -= param << 2;
			}
			break;
		}
		if (this.period < 0) {
			this.period = 0;
		}
	}

	private void portamentoDown(final int param) {
		if (this.period > 0) {
			switch (param & 0xF0) {
			case 0xE0: /* Extra-fine porta. */
				if (this.fxCount == 0) {
					this.period += param & 0xF;
				}
				break;
			case 0xF0: /* Fine porta. */
				if (this.fxCount == 0) {
					this.period += (param & 0xF) << 2;
				}
				break;
			default:/* Normal porta. */
				if (this.fxCount > 0) {
					this.period += param << 2;
				}
				break;
			}
			if (this.period > 65535) {
				this.period = 65535;
			}
		}
	}

	private void tonePortamento() {
		if (this.period > 0) {
			if (this.period < this.portaPeriod) {
				this.period += this.tonePortaParam << 2;
				if (this.period > this.portaPeriod) {
					this.period = this.portaPeriod;
				}
			} else {
				this.period -= this.tonePortaParam << 2;
				if (this.period < this.portaPeriod) {
					this.period = this.portaPeriod;
				}
			}
		}
	}

	private void vibrato(final boolean fine) {
		this.vibratoAdd = this.waveform(this.vibratoPhase, this.vibratoType & 0x3) * this.vibratoDepth >> (fine ? 7 : 5);
	}

	private void tremolo() {
		this.tremoloAdd = this.waveform(this.tremoloPhase, this.tremoloType & 0x3) * this.tremoloDepth >> 6;
	}

	private int waveform(final int phase, final int type) {
		int amplitude = 0;
		switch (type) {
		default: /* Sine. */
			amplitude = Channel.sineTable[phase & 0x1F];
			if ((phase & 0x20) > 0) {
				amplitude = -amplitude;
			}
			break;
		case 6: /* Saw Up. */
			amplitude = (((phase + 0x20) & 0x3F) << 3) - 255;
			break;
		case 1:
		case 7: /* Saw Down. */
			amplitude = 255 - (((phase + 0x20) & 0x3F) << 3);
			break;
		case 2:
		case 5: /* Square. */
			amplitude = (phase & 0x20) > 0 ? 255 : -255;
			break;
		case 3:
		case 8: /* Random. */
			amplitude = (this.randomSeed >> 20) - 255;
			this.randomSeed = (this.randomSeed * 65 + 17) & 0x1FFFFFFF;
			break;
		}
		return amplitude;
	}

	private void tremor() {
		if (this.retrigCount >= this.tremorOnTicks) {
			this.tremoloAdd = -64;
		}
		if (this.retrigCount >= (this.tremorOnTicks + this.tremorOffTicks)) {
			this.tremoloAdd = this.retrigCount = 0;
		}
	}

	private void retrigVolSlide() {
		if (this.retrigCount >= this.retrigTicks) {
			this.retrigCount = this.sampleIdx = this.sampleFra = 0;
			switch (this.retrigVolume) {
			case 0x1:
				this.volume = this.volume - 1;
				break;
			case 0x2:
				this.volume = this.volume - 2;
				break;
			case 0x3:
				this.volume = this.volume - 4;
				break;
			case 0x4:
				this.volume = this.volume - 8;
				break;
			case 0x5:
				this.volume = this.volume - 16;
				break;
			case 0x6:
				this.volume = this.volume * 2 / 3;
				break;
			case 0x7:
				this.volume = this.volume >> 1;
				break;
			case 0x8: /* ? */
				break;
			case 0x9:
				this.volume = this.volume + 1;
				break;
			case 0xA:
				this.volume = this.volume + 2;
				break;
			case 0xB:
				this.volume = this.volume + 4;
				break;
			case 0xC:
				this.volume = this.volume + 8;
				break;
			case 0xD:
				this.volume = this.volume + 16;
				break;
			case 0xE:
				this.volume = this.volume * 3 / 2;
				break;
			case 0xF:
				this.volume = this.volume << 1;
				break;
			}
			if (this.volume < 0) {
				this.volume = 0;
			}
			if (this.volume > 64) {
				this.volume = 64;
			}
		}
	}

	private void calculateFrequency() {
		int per = this.period + this.vibratoAdd;
		if (this.module.linearPeriods) {
			per = per - (this.arpeggioAdd << 6);
			if (per < 28 || per > 7680) {
				per = 7680;
			}
			this.freq = ((this.module.c2Rate >> 4) * Channel.exp2(((4608 - per) << Sample.FP_SHIFT) / 768)) >> (Sample.FP_SHIFT - 4);
		} else {
			if (per > 29021) {
				per = 29021;
			}
			per = (per << Sample.FP_SHIFT) / Channel.exp2((this.arpeggioAdd << Sample.FP_SHIFT) / 12);
			if (per < 28) {
				per = 29021;
			}
			this.freq = this.module.c2Rate * 1712 / per;
		}
	}

	private void calculateAmplitude() {
		int envVol = this.keyOn ? 64 : 0;
		if (this.instrument.volumeEnvelope.enabled) {
			envVol = this.instrument.volumeEnvelope.calculateAmpl(this.volEnvTick);
		}
		int vol = this.volume + this.tremoloAdd;
		if (vol > 64) {
			vol = 64;
		}
		if (vol < 0) {
			vol = 0;
		}
		vol = (vol * this.module.gain * Sample.FP_ONE) >> 13;
		vol = (vol * this.fadeOutVol) >> 15;
			this.ampl = (vol * this.globalVol.volume * envVol) >> 12;
			int envPan = 32;
			if (this.instrument.panningEnvelope.enabled) {
				envPan = this.instrument.panningEnvelope.calculateAmpl(this.panEnvTick);
			}
			final int panRange = (this.panning < 128) ? this.panning : (255 - this.panning);
			this.pann = this.panning + (panRange * (envPan - 32) >> 5);
	}

	private void trigger() {
		if (this.noteIns > 0 && this.noteIns <= this.module.numInstruments) {
			this.instrument = this.module.instruments[this.noteIns];
			final Sample sam = this.instrument.samples[this.instrument.keyToSample[this.noteKey < 97 ? this.noteKey : 0]];
			this.volume = sam.volume >= 64 ? 64 : sam.volume & 0x3F;
			if (sam.panning >= 0) {
				this.panning = sam.panning & 0xFF;
			}
			if (this.period > 0 && sam.looped()) {
				this.sample = sam; /* Amiga trigger. */
			}
			this.sampleOffset = this.volEnvTick = this.panEnvTick = 0;
			this.fadeOutVol = 32768;
			this.keyOn = true;
		}
		if (this.noteEffect == 0x09 || this.noteEffect == 0x8F) { /* Set Sample Offset. */
			if (this.noteParam > 0) {
				this.offsetParam = this.noteParam;
			}
			this.sampleOffset = this.offsetParam << 8;
		}
		if (this.noteVol >= 0x10 && this.noteVol < 0x60) {
			this.volume = this.noteVol < 0x50 ? this.noteVol - 0x10 : 64;
		}
		switch (this.noteVol & 0xF0) {
		case 0x80: /* Fine Vol Down. */
			this.volume -= this.noteVol & 0xF;
			if (this.volume < 0) {
				this.volume = 0;
			}
			break;
		case 0x90: /* Fine Vol Up. */
			this.volume += this.noteVol & 0xF;
			if (this.volume > 64) {
				this.volume = 64;
			}
			break;
		case 0xA0: /* Set Vibrato Speed. */
			if ((this.noteVol & 0xF) > 0) {
				this.vibratoSpeed = this.noteVol & 0xF;
			}
			break;
		case 0xB0: /* Vibrato. */
			if ((this.noteVol & 0xF) > 0) {
				this.vibratoDepth = this.noteVol & 0xF;
			}
			this.vibrato(false);
			break;
		case 0xC0: /* Set Panning. */
			this.panning = (this.noteVol & 0xF) * 17;
			break;
		case 0xF0: /* Tone Porta. */
			if ((this.noteVol & 0xF) > 0) {
				this.tonePortaParam = this.noteVol & 0xF;
			}
			break;
		}
		if (this.noteKey > 0) {
			if (this.noteKey > 96) {
				this.keyOn = false;
			} else {

				final boolean isPorta = (this.noteVol & 0xF0) == 0xF0 || this.noteEffect == 0x03 || this.noteEffect == 0x05 || this.noteEffect == 0x87 || this.noteEffect == 0x8C;
				if (!isPorta) {
					this.sample = this.instrument.samples[this.instrument.keyToSample[this.noteKey]];
				}
				int fineTune = this.sample.fineTune;
				if (this.noteEffect == 0x75 || this.noteEffect == 0xF2) { /* Set Fine Tune. */
					fineTune = (this.noteParam & 0xF) << 4;
					if (fineTune > 127) {
						fineTune -= 256;
					}
				}
				int key = this.noteKey + this.sample.relNote;
				if (key < 1) {
					key = 1;
				}
				if (key > 120) {
					key = 120;
				}
				final int per = (key << 6) + (fineTune >> 1);
				if (this.module.linearPeriods) {
					this.portaPeriod = 7744 - per;
				} else {
					this.portaPeriod = 29021 * Channel.exp2((per << Sample.FP_SHIFT) / -768) >> Sample.FP_SHIFT;
				}
				if (!isPorta) {
					this.period = this.portaPeriod;
					this.sampleIdx = this.sampleOffset;
					this.sampleFra = 0;
					if (this.vibratoType < 4) {
						this.vibratoPhase = 0;
					}
					if (this.tremoloType < 4) {
						this.tremoloPhase = 0;
					}
					this.retrigCount = this.autoVibratoCount = 0;
				}

				if (this.keyOn) {
					this.player.onChannelnote(this.id, this.volume, this.noteKey, this.globalVol.volume, this.instrument, this.panning, this.freq);
				}
			}
		}
	}

	public static int exp2(final int x) {
		final int x0 = (x & Sample.FP_MASK) >> (Sample.FP_SHIFT - 7);
					final int c = Channel.exp2Table[x0];
					final int m = Channel.exp2Table[x0 + 1] - c;
					final int y = (m * (x & (Sample.FP_MASK >> 7)) >> 8) + c;
					return (y << Sample.FP_SHIFT) >> (Sample.FP_SHIFT - (x >> Sample.FP_SHIFT));
	}

	public static int log2(final int x) {
		int y = 16 << Sample.FP_SHIFT;
		for (int step = y; step > 0; step >>= 1) {
			if (Channel.exp2(y - step) >= x) {
				y -= step;
			}
		}
		return y;
	}
}