"""The Wither's own voice (Kingdoms wither/WitherSounds), synthesised from tones and noise: nothing sampled, nothing
borrowed (Wither: Reincarnated is All Rights Reserved, so ideas only; vanilla's Wither sounds are not used either).

    python3 tools/gen_wither_sounds.py      (needs numpy and ffmpeg with libvorbis)

Its voice is three throats at once (three skulls), a little apart in pitch, each a buzzing reed through a dry, hollow
mouth, rattling as bone does. Writes mono .ogg files into kingdoms/.../assets/jaymoskingdoms/sounds/wither/:
  ambient_1..3   a slow, hollow moan from all three skulls
  hurt_1..3      a short shriek and the crack of bone
  death          a long wail falling apart into rattling bone
  spawn          a deep swell rising into a three-throated roar
  shoot_1..2     a skull spat out: a cough of breath and a thump
  beam_charge    the beam's tell: a hum rising and tightening the whole 2.75 s, crackling
  beam_fire      the beam: a crack and a tearing hiss
  charge_roar    the charge's tell: a short roar
  charge_rush    the charge: air torn past it
  stun           stunned by a shield: a dazed groan sliding down
Re-run to change them; the seeds keep every file the same from run to run.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "sounds",
				   "wither")


def spectrum_filter(x, gain):
	spec = np.fft.rfft(x)
	f = np.fft.rfftfreq(len(x), 1.0 / RATE)
	spec *= gain(f)
	return np.fft.irfft(spec, len(x))


def lowpass(x, cutoff):
	return spectrum_filter(x, lambda f: 1.0 / (1.0 + (f / cutoff) ** 4))


def bandpass(x, lo, hi):
	return spectrum_filter(x, lambda f: (1.0 / (1.0 + (f / hi) ** 4)) * (1.0 / (1.0 + (lo / np.maximum(f, 1.0)) ** 4)))


def formants(x, peaks):
	"""A hollow mouth: resonant peaks (centre, width, gain) over a damped floor."""
	def gain(f):
		g = 0.12 + 0.0 * f
		for c, w, a in peaks:
			g = g + a / (1.0 + ((f - c) / w) ** 2)
		return g
	return spectrum_filter(x, gain)


def env(n, attack, decay):
	t = np.arange(n) / RATE
	return np.minimum(1.0, t / max(attack, 1e-4)) * np.exp(-t / decay)


def shape(n, points):
	"""A curve through (time 0..1, value) points."""
	t = np.linspace(0.0, 1.0, n)
	xs, ys = zip(*points)
	return np.interp(t, xs, ys)


def norm(x, peak=0.9):
	m = np.max(np.abs(x))
	return x * (peak / m) if m > 0 else x


def reed(n, pitch, rng, rattle=30.0, rattle_depth=0.5, jitter=0.02):
	"""A buzzing reed following the pitch curve (Hz per sample): a band-limited saw with a little jitter, rattled."""
	drift = 1.0 + jitter * lowpass(rng.standard_normal(n), 6.0) * 4.0
	phase = np.cumsum(pitch * drift) / RATE
	saw = np.zeros(n)
	for k in range(1, 28):
		saw += np.sin(2 * np.pi * k * phase) / k * (1.0 / (1.0 + (k * pitch / 5000.0) ** 2))
	rat = 1.0 - rattle_depth * (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(np.full(n, rattle) * (1 + 0.2 * rng.standard_normal(n).cumsum() / n)) / RATE))
	return saw * rat


def voice(n, pitch, rng, vowel="o", rattle=30.0, depth=0.5, breath=0.25):
	"""Three throats a little apart in pitch, through one hollow mouth, with breath."""
	mouths = {
		"o": [(380, 90, 2.2), (820, 140, 1.4), (2400, 300, 0.4)],
		"a": [(650, 120, 2.2), (1100, 160, 1.6), (2600, 320, 0.5)],
		"u": [(280, 70, 2.4), (650, 110, 1.0), (2200, 260, 0.25)],
		"e": [(480, 100, 1.8), (1800, 220, 1.4), (2700, 300, 0.6)],
	}
	x = np.zeros(n)
	for k, (ratio, gain) in enumerate(((1.0, 1.0), (1.26, 0.6), (0.79, 0.7))):
		x += gain * reed(n, pitch * ratio, rng, rattle * (1 + 0.13 * k), depth)
	x = formants(x, mouths[vowel])
	x += breath * bandpass(rng.standard_normal(n), 400, 3500) * np.max(np.abs(x)) * 0.3
	return np.tanh(norm(x, 1.0) * 1.8)


def bone(n, rng, at, count, spread):
	"""Dry clicks and cracks of bone, scattered round {at} seconds."""
	x = np.zeros(n)
	for _ in range(count):
		s = int(max(0, min(n - 2000, (at + rng.normal(0, spread)) * RATE)))
		m = min(n - s, int(RATE * 0.05))
		click = rng.standard_normal(m) * np.exp(-np.arange(m) / (RATE * 0.006))
		x[s:s + m] += bandpass(click, 900, 6000) * rng.uniform(0.3, 1.0)
	return x


def hall(x, length=1.2, mix=0.25, seed=7):
	"""A dark, stony space round it."""
	rng = np.random.default_rng(seed)
	m = int(RATE * length)
	ir = rng.standard_normal(m) * np.exp(-np.arange(m) / (RATE * length / 5))
	ir = lowpass(ir, 2500)
	wet = np.fft.irfft(np.fft.rfft(x, len(x) + m) * np.fft.rfft(ir, len(x) + m), len(x) + m)[:len(x) + m]
	out = np.concatenate([x, np.zeros(m)])
	return norm(out + norm(wet, np.max(np.abs(x))) * mix, 0.9)


def fade(x, ms=60):
	m = int(RATE * ms / 1000)
	x[-m:] *= np.linspace(1, 0, m)
	return x


def write(name, x):
	os.makedirs(OUT, exist_ok=True)
	x = fade(norm(x, 0.89))
	pcm = (x * 32767).astype("<i2").tobytes()
	with tempfile.TemporaryDirectory() as tmp:
		path = os.path.join(tmp, name + ".wav")
		with wave.open(path, "wb") as w:
			w.setnchannels(1)
			w.setsampwidth(2)
			w.setframerate(RATE)
			w.writeframes(pcm)
		subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "5",
						os.path.join(OUT, name + ".ogg")], check=True)


def seconds(s):
	return int(RATE * s)


def ambient(seed):
	rng = np.random.default_rng(seed)
	length = rng.uniform(1.6, 2.3)
	n = seconds(length)
	base = rng.uniform(62, 80)
	pitch = base * shape(n, [(0, 1.0), (0.3, 1.12), (0.7, 0.95), (1, 0.82)])
	x = voice(n, pitch, rng, rng.choice(["o", "u", "a"]), rattle=rng.uniform(22, 34), depth=0.55)
	x *= shape(n, [(0, 0), (0.15, 1), (0.6, 0.85), (1, 0)])
	return hall(x, 1.0, 0.3, seed)


def hurt(seed):
	rng = np.random.default_rng(seed)
	n = seconds(0.55)
	pitch = rng.uniform(150, 190) * shape(n, [(0, 1.3), (0.2, 1.0), (1, 0.7)])
	x = voice(n, pitch, rng, "e", rattle=45, depth=0.4, breath=0.4) * env(n, 0.01, 0.18)
	x += bone(n, rng, 0.02, 6, 0.015) * 0.8
	return hall(x, 0.6, 0.2, seed)


def death():
	rng = np.random.default_rng(31)
	n = seconds(4.2)
	pitch = 140 * shape(n, [(0, 1.0), (0.15, 1.25), (0.5, 0.7), (1, 0.32)])
	x = voice(n, pitch, rng, "a", rattle=28, depth=0.6, breath=0.5)
	x *= shape(n, [(0, 0), (0.05, 1), (0.6, 0.7), (1, 0)])
	x += bone(n, rng, 2.8, 40, 0.5) * 0.6
	return hall(x, 2.0, 0.35, 31)


def spawn():
	rng = np.random.default_rng(41)
	n = seconds(5.0)
	rumble = lowpass(rng.standard_normal(n), 90) * shape(n, [(0, 0), (0.6, 1), (0.85, 1), (1, 0)]) * 8
	pitch = 48 * shape(n, [(0, 0.8), (0.6, 1.0), (0.75, 1.9), (1, 1.6)])
	roar = voice(n, pitch, rng, "a", rattle=26, depth=0.5, breath=0.6) * shape(n, [(0, 0), (0.55, 0.3), (0.75, 1), (1, 0)])
	return hall(np.tanh(rumble) * 0.7 + roar, 2.2, 0.35, 41)


def shoot(seed):
	rng = np.random.default_rng(seed)
	n = seconds(0.45)
	cough = bandpass(rng.standard_normal(n), 200, 2200) * env(n, 0.005, 0.07)
	thump = np.sin(2 * np.pi * np.cumsum(90 * shape(n, [(0, 1.6), (1, 0.6)])) / RATE) * env(n, 0.002, 0.09)
	grunt = voice(n, 110 * shape(n, [(0, 1.2), (1, 0.8)]), rng, "u", rattle=40, depth=0.3) * env(n, 0.01, 0.1)
	return hall(cough * 0.9 + thump + grunt * 0.6, 0.5, 0.2, seed)


def beam_charge():
	rng = np.random.default_rng(51)
	n = seconds(2.9)
	pitch = 70 * shape(n, [(0, 1.0), (0.7, 2.4), (0.95, 3.2), (1, 3.3)])
	hum = np.zeros(n)
	phase = np.cumsum(pitch) / RATE
	for k, a in ((1, 1.0), (2, 0.5), (3, 0.35), (5, 0.2), (8, 0.12)):
		hum += a * np.sin(2 * np.pi * k * phase)
	trem = 1.0 - 0.5 * (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(shape(n, [(0, 4), (1, 22)])) / RATE))
	hum *= trem * shape(n, [(0, 0), (0.2, 0.4), (0.9, 1), (1, 0.8)])
	crackle = np.zeros(n)
	for _ in range(160):
		s = int(rng.uniform(0.2, 0.98) ** 0.6 * (n - 800))
		crackle[s:s + 400] += rng.standard_normal(400) * np.exp(-np.arange(400) / 60)
	crackle = bandpass(crackle, 2000, 9000) * shape(n, [(0, 0), (1, 1)])
	growl = voice(n, 55 * shape(n, [(0, 1), (1, 1.3)]), rng, "u", rattle=30, depth=0.6) * 0.35
	return hall(np.tanh(hum * 1.5) + crackle * 0.5 + growl, 1.2, 0.25, 51)


def beam_fire():
	rng = np.random.default_rng(61)
	n = seconds(1.3)
	crack = bandpass(rng.standard_normal(n), 300, 9000) * env(n, 0.001, 0.03) * 1.6
	tear = bandpass(rng.standard_normal(n), 1500, 7000) * shape(n, [(0, 0), (0.03, 1), (0.55, 0.6), (1, 0)])
	tone = np.sin(2 * np.pi * np.cumsum(230 * shape(n, [(0, 1.2), (1, 0.9)])) / RATE) * shape(n, [(0, 0), (0.03, 0.6), (1, 0)])
	boom = np.sin(2 * np.pi * np.cumsum(60 * shape(n, [(0, 1.4), (1, 0.7)])) / RATE) * env(n, 0.003, 0.25)
	return hall(crack + tear * 0.6 + tone * 0.4 + boom, 1.5, 0.3, 61)


def charge_roar():
	rng = np.random.default_rng(71)
	n = seconds(1.5)
	pitch = 85 * shape(n, [(0, 0.8), (0.25, 1.15), (0.8, 1.05), (1, 0.85)])
	x = voice(n, pitch, rng, "a", rattle=24, depth=0.55, breath=0.7) * shape(n, [(0, 0), (0.12, 1), (0.8, 0.9), (1, 0)])
	return hall(x, 1.2, 0.3, 71)


def charge_rush():
	rng = np.random.default_rng(81)
	n = seconds(1.4)
	wind = np.zeros(n)
	for lo, hi, a in ((150, 900, 1.0), (900, 3500, 0.5)):
		wind += a * bandpass(rng.standard_normal(n), lo, hi)
	sweep = shape(n, [(0, 0), (0.15, 1), (0.7, 0.8), (1, 0)])
	whoosh = np.sin(2 * np.pi * np.cumsum(120 * shape(n, [(0, 0.6), (0.5, 1.2), (1, 0.7)])) / RATE) * sweep * 0.25
	return hall(wind * sweep + whoosh, 0.8, 0.2, 81)


def stun():
	rng = np.random.default_rng(91)
	n = seconds(1.8)
	pitch = 120 * shape(n, [(0, 1.1), (0.3, 0.9), (1, 0.45)])
	x = voice(n, pitch, rng, "o", rattle=18, depth=0.7, breath=0.5) * shape(n, [(0, 0), (0.08, 1), (0.7, 0.6), (1, 0)])
	x += bone(n, rng, 0.1, 10, 0.06) * 0.5
	return hall(x, 1.2, 0.3, 91)


def main():
	for i in range(3):
		write(f"ambient_{i + 1}", ambient(100 + i))
		write(f"hurt_{i + 1}", hurt(200 + i))
	for i in range(2):
		write(f"shoot_{i + 1}", shoot(300 + i))
	write("death", death())
	write("spawn", spawn())
	write("beam_charge", beam_charge())
	write("beam_fire", beam_fire())
	write("charge_roar", charge_roar())
	write("charge_rush", charge_rush())
	write("stun", stun())


if __name__ == "__main__":
	main()
