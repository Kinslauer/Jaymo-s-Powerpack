"""The ki blade's sounds, synthesised from tones and noise (nothing sampled, nothing borrowed).

    python3 tools/gen_ki_blade_sounds.py      (needs numpy and ffmpeg with libvorbis)

Writes mono .ogg files into src/main/resources/assets/jaymospowers/sounds/ki_blade/:
  ignite_1..2    the ki rushing out into a blade: a tone climbing fast, with a spit of sparks
  retract_1..2   the blade drawn back in: the same tone falling away
  hum_1..2       two seconds of the blade held: a low chord that beats slowly, soft at both ends so plays overlap
  swing_1..4     a cut: the hum swept up and down as the blade passes
  deflect_1..3   a shot struck aside: a hard crack and a short ringing tail
Re-run to change them; the seeds keep every file the same from run to run.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymospowers", "sounds", "ki_blade")


def band(x, low, high, order=4):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / RATE)
    spec *= 1.0 / (1.0 + (f / high) ** order)
    spec *= 1.0 - 1.0 / (1.0 + (f / max(low, 1.0)) ** order)
    return np.fft.irfft(spec, len(x))


def save(name, x):
    x = np.asarray(x, dtype=np.float64)
    x = x / np.max(np.abs(x)) * 0.85
    pcm = (x * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, name + ".wav")
        with wave.open(path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "5",
                        os.path.join(OUT, name + ".ogg")], check=True)


def chord(freq, rng, n):
    """The blade's voice at a pitch that may move: three partials a little out of tune with each other, and a breath of air."""
    phase = 2.0 * np.pi * np.cumsum(freq) / RATE
    wobble = 1.0 + 0.25 * np.sin(2.0 * np.pi * rng.uniform(5.0, 7.0) * np.arange(n) / RATE)
    tone = np.sin(phase) + 0.55 * np.sin(1.503 * phase + 1.0) + 0.3 * np.sin(2.01 * phase + 2.0) + 0.18 * np.sign(np.sin(0.5 * phase))
    air = band(rng.standard_normal(n), 900, 4200) * 0.22
    return tone * wobble + air


def ignite(rng):
    n = int(0.55 * RATE)
    t = np.arange(n) / n
    body = chord(70.0 + 95.0 * t ** 0.5, rng, n) * np.minimum(1.0, t * 9.0) * (1.0 - 0.45 * t)
    sparks = band(rng.standard_normal(n), 2500, 9000) * np.exp(-t * 14.0) * 0.9
    return body + sparks


def retract(rng):
    n = int(0.45 * RATE)
    t = np.arange(n) / n
    return chord(165.0 - 110.0 * t ** 0.7, rng, n) * (1.0 - t) ** 1.5


def hum(rng):
    n = int(2.0 * RATE)
    t = np.arange(n) / n
    edge = np.minimum(1.0, np.minimum(t, 1.0 - t) * 6.0)
    return chord(np.full(n, rng.uniform(158.0, 168.0)), rng, n) * edge


def swing(rng):
    n = int(rng.uniform(0.3, 0.4) * RATE)
    t = np.arange(n) / n
    bend = np.exp(-((t - 0.45) ** 2) / 0.03)
    return chord(160.0 + rng.uniform(110.0, 150.0) * bend, rng, n) * (0.25 + bend) * np.minimum(1.0, np.minimum(t, 1.0 - t) * 12.0)


def deflect(rng):
    n = int(0.4 * RATE)
    t = np.arange(n) / RATE
    crack = band(rng.standard_normal(n), 1500, 9000) * np.exp(-t * 60.0)
    base = rng.uniform(620.0, 760.0)
    ring = sum(np.sin(2.0 * np.pi * base * m * t + rng.uniform(0, 6.28)) * np.exp(-t * (9.0 + 4.0 * i)) / (1.0 + i)
               for i, m in enumerate((1.0, 1.47, 2.09, 2.9)))
    return crack * 1.2 + ring * 0.6


def main():
    for name, make, count, seed in (("ignite", ignite, 2, 500), ("retract", retract, 2, 520), ("hum", hum, 2, 540),
                                    ("swing", swing, 4, 560), ("deflect", deflect, 3, 580)):
        for i in range(count):
            save("%s_%d" % (name, i + 1), make(np.random.default_rng(seed + i)))


if __name__ == "__main__":
    main()
