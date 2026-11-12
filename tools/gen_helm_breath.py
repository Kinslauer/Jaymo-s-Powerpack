"""Your own breath inside a great helm, synthesised from noise (nothing sampled, nothing borrowed).

    python3 tools/gen_helm_breath.py      (needs numpy and ffmpeg with libvorbis)

Writes mono src/main/resources/assets/jaymospowers/sounds/helm/breath_1..3.ogg: one breath in and one out, low and
muffled as it sounds against steel. Re-run to change them; the seeds keep every file the same from run to run.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymospowers", "sounds", "helm")


def band(x, low, high, order=4):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / RATE)
    spec *= 1.0 / (1.0 + (f / high) ** order)
    spec *= 1.0 - 1.0 / (1.0 + (f / max(low, 1.0)) ** order)
    return np.fft.irfft(spec, len(x))


def save(name, x):
    x = np.asarray(x, dtype=np.float64)
    x = x / np.max(np.abs(x)) * 0.8
    pcm = (x * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, name + ".wav")
        with wave.open(path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "4",
                        os.path.join(OUT, name + ".ogg")], check=True)


def half(rng, seconds, low, high, rise):
    """Half a breath: muffled noise that swells and falls away."""
    n = int(seconds * RATE)
    t = np.arange(n) / n
    env = np.sin(np.pi * t ** rise) ** 2
    return band(rng.standard_normal(n), low, high) * env


def breath(rng):
    breath_in = half(rng, rng.uniform(0.75, 0.9), 250, 900, 0.8) * 0.7
    gap = np.zeros(int(rng.uniform(0.12, 0.2) * RATE))
    breath_out = half(rng, rng.uniform(0.95, 1.15), 120, 600, 0.6)
    return np.concatenate([breath_in, gap, breath_out])


def main():
    for i in range(3):
        save("breath_%d" % (i + 1), breath(np.random.default_rng(400 + i)))


if __name__ == "__main__":
    main()
