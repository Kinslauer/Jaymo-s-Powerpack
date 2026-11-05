"""(0.20.56) Our own blast and shell sounds, synthesised from noise and tones (nothing sampled, nothing borrowed).

    python3 tools/gen_blast_sounds.py      (needs numpy and ffmpeg with libvorbis)

Writes mono .ogg files into src/main/resources/assets/jaymospowers/sounds/blast/:
  crack_1..3   the near layer of a blast: a sharp broadband crack and a short patter of debris
  far_1..3     the far layer: a low thump and a long rolling rumble, what a blast sounds like a long way off
  report_1..2  a cannon's report: a hard concussive thump with a short low tail
  whistle      a shell in flight, made to loop: a whining tone over a rushing hiss
Re-run to change them; the seeds keep every file the same from run to run.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymospowers", "sounds", "blast")


def lowpass(x, cutoff):
    """A gentle one-pole low pass, run twice."""
    a = np.exp(-2.0 * np.pi * cutoff / RATE)
    for _ in range(2):
        y = np.empty_like(x)
        acc = 0.0
        for i in range(len(x)):
            acc = (1 - a) * x[i] + a * acc
            y[i] = acc
        x = y
    return x


def lowpass_fast(x, cutoff):
    """The same filter done in the frequency domain (for long noise beds)."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / RATE)
    spec *= 1.0 / (1.0 + (f / cutoff) ** 4)
    return np.fft.irfft(spec, len(x))


def bandpass_fast(x, lo, hi):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / RATE)
    spec *= (1.0 / (1.0 + (f / hi) ** 4)) * (1.0 / (1.0 + (lo / np.maximum(f, 1.0)) ** 4))
    return np.fft.irfft(spec, len(x))


def env(n, attack, decay):
    t = np.arange(n) / RATE
    return np.minimum(1.0, t / max(attack, 1e-4)) * np.exp(-t / decay)


def norm(x, peak=0.9):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def write(name, x):
    os.makedirs(OUT, exist_ok=True)
    x = np.clip(x, -1, 1)
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "5",
                    os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(path)


def thump(n, rng, f0, f1, decay):
    """A falling sine (the chest-punch of a blast)."""
    t = np.arange(n) / RATE
    f = f1 + (f0 - f1) * np.exp(-t / 0.08)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    return np.sin(phase + rng.uniform(0, np.pi)) * env(n, 0.002, decay)


def crack(seed):
    rng = np.random.default_rng(seed)
    n = int(RATE * 1.4)
    noise = rng.standard_normal(n)
    hit = noise * env(n, 0.0005, 0.035)
    body = lowpass_fast(rng.standard_normal(n), 1800) * env(n, 0.003, 0.22) * 0.9
    boom = thump(n, rng, 160, 45, 0.35) * 0.9
    # debris: a scatter of small clicks that thin out
    debris = np.zeros(n)
    for _ in range(70):
        at = int(RATE * (0.08 + rng.exponential(0.25)))
        if at >= n - 600:
            continue
        k = int(rng.uniform(80, 500))
        click = bandpass_fast(rng.standard_normal(k), 900, 6000) * env(k, 0.0003, 0.004)
        debris[at:at + k] += click * rng.uniform(0.05, 0.25) * np.exp(-at / RATE / 0.5)
    x = hit + body + boom + debris
    x *= np.minimum(1.0, (n - np.arange(n)) / (RATE * 0.05))
    return norm(x)


def far(seed):
    rng = np.random.default_rng(seed)
    n = int(RATE * 3.6)
    t = np.arange(n) / RATE
    boom = thump(n, rng, 90, 32, 0.6)
    rumble = lowpass_fast(rng.standard_normal(n), 140) * env(n, 0.04, 1.1)
    # the roll: a few late swells, echoes off hills
    swell = np.zeros(n)
    for k in range(4):
        at = rng.uniform(0.25, 1.6)
        w = rng.uniform(0.3, 0.7)
        swell += np.exp(-((t - at) / w) ** 2) * rng.uniform(0.25, 0.6)
    rumble *= 1.0 + swell
    x = boom * 1.2 + rumble * 2.2
    x *= np.minimum(1.0, (n - np.arange(n)) / (RATE * 0.3))
    return norm(x, 0.95)


def report(seed):
    rng = np.random.default_rng(seed)
    n = int(RATE * 1.8)
    hit = lowpass_fast(rng.standard_normal(n), 3000) * env(n, 0.0008, 0.05) * 1.2
    boom = thump(n, rng, 120, 38, 0.45) * 1.4
    tail = lowpass_fast(rng.standard_normal(n), 300) * env(n, 0.02, 0.5) * 0.9
    x = hit + boom + tail
    x *= np.minimum(1.0, (n - np.arange(n)) / (RATE * 0.1))
    return norm(x)


def whistle():
    """A two-second loop: every part repeats a whole number of times, and the noise is crossfaded round."""
    rng = np.random.default_rng(77)
    seconds = 2.0
    n = int(RATE * seconds)
    t = np.arange(n) / RATE
    vib = 0.012 * np.sin(2 * np.pi * 3.0 * t)  # 6 wobbles in the loop
    f = 820.0 * (1.0 + vib)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    # snap the tone so it ends where it started
    phase *= np.round(phase[-1] / (2 * np.pi)) * 2 * np.pi / phase[-1]
    tone = 0.35 * np.sin(phase) + 0.12 * np.sin(2 * phase + 0.4)
    m = n + RATE // 4
    hiss = bandpass_fast(rng.standard_normal(m), 400, 3500)
    fade = np.linspace(0, 1, RATE // 4)
    loop = hiss[:n].copy()
    loop[:RATE // 4] = hiss[:RATE // 4] * fade + hiss[n:] * (1 - fade)
    rush = loop * (0.8 + 0.2 * np.sin(2 * np.pi * 1.5 * t))
    return norm(tone + rush * 0.7, 0.8)


if __name__ == "__main__":
    for i in range(3):
        write(f"crack_{i + 1}", crack(10 + i))
        write(f"far_{i + 1}", far(20 + i))
    for i in range(2):
        write(f"report_{i + 1}", report(30 + i))
    write("whistle", whistle())
    print("wrote", sorted(os.listdir(OUT)))
