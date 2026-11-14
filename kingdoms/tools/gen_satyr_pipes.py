"""Writes the satyrs' pipe tunes (Satyr groves), our own, synthesised from scratch: reed pipes over a low drone.

Each tune is a few short phrases in an old mode (Dorian, Phrygian, Mixolydian), played AABA with small changes on the
repeats, a breathy pan-pipe voice (a sine with a little of its third harmonic, a puff of breath on each note, a slow
vibrato) over a soft drone on the mode's root. Mono, so the game places them at the piper.

  python3 kingdoms/tools/gen_satyr_pipes.py   (needs numpy and ffmpeg with libvorbis)

Writes kingdoms/src/main/resources/assets/jaymoskingdoms/sounds/satyr/pipes_{1,2,3}.ogg
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "jaymoskingdoms", "sounds", "satyr")

# Semitone steps of each mode from its root, over two octaves.
MODES = {"dorian": [0, 2, 3, 5, 7, 9, 10], "phrygian": [0, 1, 3, 5, 7, 8, 10], "mixolydian": [0, 2, 4, 5, 7, 9, 10]}
# (mode, root as a MIDI note, beats a minute, seed)
TUNES = [("dorian", 62, 104, 11), ("phrygian", 64, 92, 23), ("mixolydian", 67, 116, 37)]


def freq(midi):
	return 440.0 * 2 ** ((midi - 69) / 12.0)


def pitch(mode, root, degree):
	steps = MODES[mode]
	octave, i = divmod(degree, len(steps))
	return root + 12 * octave + steps[i]


def pipe_note(f, dur, rng, loud=1.0):
	n = int(dur * SR)
	t = np.arange(n) / SR
	vib = 1.0 + 0.004 * np.sin(2 * np.pi * 5.2 * t + rng.random() * 6.28) * np.clip(t / 0.25, 0, 1)
	phase = 2 * np.pi * np.cumsum(f * vib) / SR
	tone = np.sin(phase) + 0.18 * np.sin(3 * phase) + 0.06 * np.sin(2 * phase)
	breath = rng.standard_normal(n)
	# the breath, filtered round the note: a cheap resonator
	k = np.exp(-2 * np.pi * 900 / SR)
	filt = np.zeros(n)
	acc = 0.0
	for i in range(n):
		acc = (1 - k) * breath[i] + k * acc
		filt[i] = acc
	filt *= np.sin(phase * 0.5) ** 2
	attack = np.clip(t / 0.045, 0, 1)
	release = np.clip((dur - t) / 0.07, 0, 1)
	puff = np.exp(-t / 0.05) * 0.5
	env = attack * release
	return loud * env * (0.55 * tone + (0.12 + puff) * filt * 2.2)


def phrase(rng, length, mode):
	"""A phrase: a walk over the mode's steps (mostly by step, now and then a leap), with its rhythm in beats."""
	degree, notes = 4 + rng.integers(0, 3), []
	beats = 0.0
	while beats < length:
		dur = rng.choice([0.5, 0.5, 0.5, 1.0, 1.0, 0.25, 0.25, 1.5])
		if beats + dur > length:
			dur = length - beats
		move = rng.choice([-1, -1, 1, 1, -2, 2, 0, 3, -3])
		degree = int(np.clip(degree + move, 0, 11))
		notes.append((degree, dur))
		beats += dur
	# every phrase comes home to the root or the fifth, held
	last, dur = notes[-1]
	notes[-1] = (rng.choice([0, 4, 7]), max(dur, 1.0))
	return notes


def vary(rng, notes):
	"""A repeat with a few notes turned."""
	out = []
	for d, b in notes:
		if rng.random() < 0.25:
			d = int(np.clip(d + rng.choice([-1, 1]), 0, 11))
		out.append((d, b))
	out[-1] = notes[-1]
	return out


def tune(mode, root, bpm, seed):
	rng = np.random.default_rng(seed)
	beat = 60.0 / bpm
	a, b = phrase(rng, 8, mode), phrase(rng, 8, mode)
	melody = a + vary(rng, a) + b + vary(rng, a) + a
	total = sum(d for _, d in melody) * beat + 1.5
	out = np.zeros(int(total * SR) + SR)
	t = 0.0
	for degree, beats in melody:
		f = freq(pitch(mode, root, degree))
		dur = beats * beat
		note = pipe_note(f, dur * 0.96, rng, 0.8 + 0.2 * rng.random())
		i = int(t * SR)
		out[i:i + len(note)] += note
		t += dur
	# the drone: the root an octave down, breathing in and out under the tune
	n = int(t * SR)
	tt = np.arange(n) / SR
	f0 = freq(root - 12)
	drone = (np.sin(2 * np.pi * f0 * tt) + 0.3 * np.sin(2 * np.pi * 1.5 * f0 * tt)) * (0.16 + 0.04 * np.sin(2 * np.pi * tt / (beat * 8)))
	drone *= np.clip(tt / 1.0, 0, 1) * np.clip((t - tt) / 1.0, 0, 1)
	out[:n] += drone
	out = out[:int((t + 1.0) * SR)]
	return out / (np.max(np.abs(out)) + 1e-9) * 0.85


def write(samples, path):
	with tempfile.TemporaryDirectory() as tmp:
		wav = os.path.join(tmp, "t.wav")
		with wave.open(wav, "wb") as w:
			w.setnchannels(1)
			w.setsampwidth(2)
			w.setframerate(SR)
			w.writeframes((samples * 32767).astype(np.int16).tobytes())
		subprocess.check_call(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", path])


def main():
	os.makedirs(OUT, exist_ok=True)
	for i, (mode, root, bpm, seed) in enumerate(TUNES, 1):
		s = tune(mode, root, bpm, seed)
		path = os.path.join(OUT, f"pipes_{i}.ogg")
		write(s, path)
		print(f"wrote sounds/satyr/pipes_{i}.ogg ({len(s) / SR:.1f} s, {mode})")


if __name__ == "__main__":
	main()
