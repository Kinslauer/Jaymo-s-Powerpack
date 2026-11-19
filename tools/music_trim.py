#!/usr/bin/env python3
"""Builds "Jaymo Medieval Music.zip": Sarisan's 26.3 port of Alexander Nakarada's Medieval Music pack, with every ogg
re-encoded at a lower bitrate (the original is ~490 MB; this is ~120 MB).

    python3 tools/music_trim.py [--src Medieval-Music.zip] [--out dist/music] [--bitrate 80k]

Without --src it downloads Modrinth version vz46Jajc (project J0ZvJyuZ). Needs ffmpeg (libvorbis) on the PATH.
It keeps sounds.json, pack.mcmeta, pack.png and the lang file, and replaces credits.txt with CREDITS.txt (ours, then the
author's own credits). Writes the zip and its SHA-1 as "Jaymo.Medieval.Music.zip" and "Jaymo.Medieval.Music.zip.sha1":
GitHub turns spaces in an asset's name into dots, and PackResourcePacks (client) fetches exactly those names from the
release tagged "medieval-music-v1"
(https://github.com/Kinslauer/Jaymo-s-Powerpack/releases/download/medieval-music-v1/).
Upload both files to that release by hand; this script never does.
"""
import argparse
import hashlib
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

SOURCE_URL = "https://cdn.modrinth.com/data/J0ZvJyuZ/versions/vz46Jajc/Medieval%20Music.zip"
SOURCE_SHA1 = "7c6b411ccdba27f01d6a71527ef28d71066529c0"
ZIP_NAME = "Jaymo Medieval Music.zip"
ASSET_NAME = "Jaymo.Medieval.Music.zip"
KEEP = ("assets/minecraft/sounds.json", "assets/minecraft/lang/en_us.json", "pack.mcmeta", "pack.png")

CREDITS = """Jaymo Medieval Music
====================
Music: Alexander Nakarada (https://creatorchords.com), licensed under CC BY 4.0
(https://creativecommons.org/licenses/by/4.0/).
Minecraft resource pack port: Sarisan, "Medieval Music" on Modrinth, licensed under CC BY 4.0.
Changes made: every track was re-encoded at a lower bitrate (Ogg Vorbis, about 80 kbps) so the pack downloads
faster. The sounds.json, pack.mcmeta and pack.png are Sarisan's, unchanged.

The author's own credits file follows.

"""


def fetch(url: str, to: Path) -> None:
    print("downloading", url)
    with urllib.request.urlopen(url) as src, open(to, "wb") as out:
        shutil.copyfileobj(src, out)


def sha1(path: Path) -> str:
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", type=Path, help="a local copy of Sarisan's zip (default: download it)")
    ap.add_argument("--out", type=Path, default=Path("dist/music"))
    ap.add_argument("--bitrate", default="80k")
    args = ap.parse_args()
    if shutil.which("ffmpeg") is None:
        print("ffmpeg is not on the PATH", file=sys.stderr)
        return 1
    args.out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        src = args.src
        if src is None:
            src = tmp / "source.zip"
            fetch(SOURCE_URL, src)
            if sha1(src) != SOURCE_SHA1:
                print("the download's SHA-1 isn't the one this script was written against", file=sys.stderr)
                return 1
        asset = args.out / ASSET_NAME
        with zipfile.ZipFile(src) as zin, zipfile.ZipFile(asset, "w", zipfile.ZIP_DEFLATED) as zout:
            for info in zin.infolist():
                name = info.filename
                if info.is_dir():
                    continue
                if name in KEEP:
                    zout.writestr(name, zin.read(name))
                elif name.lower() == "credits.txt":
                    zout.writestr("CREDITS.txt", CREDITS + zin.read(name).decode("utf-8", "replace"))
                elif name.endswith(".ogg"):
                    raw, small = tmp / "in.ogg", tmp / "out.ogg"
                    raw.write_bytes(zin.read(name))
                    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(raw), "-vn", "-map_metadata", "-1",
                                    "-c:a", "libvorbis", "-b:a", args.bitrate, str(small)], check=True)
                    zout.write(small, name)
                    print(f"{name}: {raw.stat().st_size // 1024} KB -> {small.stat().st_size // 1024} KB")
                else:
                    print("skipped", name)
        digest = sha1(asset)
        (args.out / (ASSET_NAME + ".sha1")).write_text(digest + "\n")
        print(f"wrote {asset} ({asset.stat().st_size // (1 << 20)} MB), SHA-1 {digest}")
        print(f"On the PC it is saved as '{ZIP_NAME}' in resourcepacks/ by the game itself.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
