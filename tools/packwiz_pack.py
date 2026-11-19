#!/usr/bin/env python3
"""Builds the testers' packwiz pack (docs/Tester-Pack.md) from build/packwiz-list.json (gradlew packwizList).

  python3 tools/packwiz_pack.py build/packwiz-list.json --out build/tester-pack [--previous DIR] [--publish]

Writes pack.toml, index.toml and one .pw.toml per file:
  - Modrinth mods point at Modrinth's own download, found by the jar's SHA-1 (so it is exactly the jar Gradle resolved).
    A jar Modrinth doesn't know stops the run: third-party jars are never re-hosted by us.
  - Our jars and our ports (Powers, Kingdoms, Kitchen, MCA's 26.3 build, TRMT, Dynamic Waters) point at assets of the
    repo's RELEASE_TAG release. An asset is named after the jar's contents (not its build time), so a rebuild of
    unchanged code reuses the asset already there; its label holds its SHA-256, so it never has to be downloaded again.
  - Resource packs, shader packs and CurseForge-only mods are fetched by packwiz through CurseForge by their ids.
With --publish (CI, needs `gh` and GH_TOKEN) it uploads missing jars, keeps the Prism instance zip up to date, and
deletes assets neither this pack nor the previous one (--previous: the last published pack) points at. Without it,
nothing is uploaded: a dry run that still writes every file.
"""
import argparse
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
import zipfile

REPO = "Kinslauer/jaymo-s-powerpack"
BRANCH = "tester-pack"  # the branch holding the pack's text files (pack.toml, index.toml, *.pw.toml)
RELEASE_TAG = "tester-pack-files"  # the release holding our jars and the Prism instance zip
PACK_URL = f"https://raw.githubusercontent.com/{REPO}/{BRANCH}/pack.toml"
PRISM_ZIP = "Jaymos-Powers-Prism.zip"
BOOTSTRAP_URL = "https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/packwiz-installer-bootstrap.jar"
USER_AGENT = "jaymos-powers-tester-pack/1.0"
MEMORY_MB = 8192  # the Prism instance's -Xmx: the pack is heavy


def sha(algo, data):
    return hashlib.new(algo, data).hexdigest()


def q(s):
    # a TOML basic string (JSON's escapes are valid TOML ones)
    return json.dumps(str(s), ensure_ascii=False)


def http(url, data=None, tries=5):
    for attempt in range(1, tries + 1):
        req = urllib.request.Request(url, data=data, headers={"User-Agent": USER_AGENT, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code not in (429, 500, 502, 503, 504) or attempt == tries:
                raise
        except urllib.error.URLError:
            if attempt == tries:
                raise
        time.sleep(attempt * 10)


def fabric_meta(jar_bytes):
    try:
        with zipfile.ZipFile(io.BytesIO(jar_bytes)) as z:
            return json.loads(z.read("fabric.mod.json").decode("utf-8"), strict=False)
    except (KeyError, ValueError, zipfile.BadZipFile):
        return {}


def content_id(jar_bytes):
    """A hash of what a jar holds, blind to the times stored in it (nested jars by their own contents)."""
    h = hashlib.sha256()
    with zipfile.ZipFile(io.BytesIO(jar_bytes)) as z:
        for name in sorted(z.namelist()):
            data = z.read(name)
            part = content_id(data) if name.endswith(".jar") else sha("sha256", data)
            h.update(name.encode() + b"\0" + part.encode() + b"\n")
    return h.hexdigest()


def slug(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-") or "file"


def safe_asset(name):
    # GitHub rewrites unusual characters in asset names; keep to the ones it leaves alone
    return re.sub(r"[^A-Za-z0-9._-]", "-", name)


def gh(*args, capture=True):
    r = subprocess.run(["gh", *args], check=True, text=True, capture_output=capture)
    return r.stdout if capture else ""


class Release:
    """The RELEASE_TAG release's assets: name -> label (our SHA-256)."""

    def __init__(self, publish):
        self.publish = publish
        self.assets = {}
        if not publish:
            return
        try:
            out = gh("release", "view", RELEASE_TAG, "--repo", REPO, "--json", "assets")
        except subprocess.CalledProcessError:
            gh("release", "create", RELEASE_TAG, "--repo", REPO, "--prerelease", "--title", "Tester pack files",
               "--notes", "Jars and the Prism instance for the testers' packwiz pack (docs/Tester-Pack.md). "
                          "Kept up to date by the Tester pack workflow; don't edit by hand.")
            out = gh("release", "view", RELEASE_TAG, "--repo", REPO, "--json", "assets")
        for a in json.loads(out)["assets"]:
            self.assets[a["name"]] = a.get("label") or ""

    def url(self, name):
        return f"https://github.com/{REPO}/releases/download/{RELEASE_TAG}/{urllib.parse.quote(name)}"

    def ensure(self, name, data, label):
        """Uploads data as asset `name` unless it's there with this label."""
        if self.assets.get(name) == label:
            return
        if self.publish:
            with tempfile.TemporaryDirectory() as tmp:
                path = os.path.join(tmp, name)
                with open(path, "wb") as f:
                    f.write(data)
                gh("release", "upload", RELEASE_TAG, f"{path}#{label}", "--repo", REPO, "--clobber")
            print(f"uploaded {name}")
        self.assets[name] = label

    def prune(self, keep):
        for name in sorted(self.assets):
            if name not in keep and self.publish:
                gh("release", "delete-asset", RELEASE_TAG, name, "--repo", REPO, "--yes")
                print(f"deleted old asset {name}")


def modrinth_versions(sha1s):
    found = {}
    for i in range(0, len(sha1s), 100):
        body = json.dumps({"hashes": sha1s[i:i + 100], "algorithm": "sha1"}).encode()
        found.update(json.loads(http("https://api.modrinth.com/v2/version_files", data=body)))
    return found


def cf_url(file_id, file_name):
    i = str(file_id)
    return f"https://mediafilez.forgecdn.net/files/{i[:4]}/{i[4:].lstrip('0') or '0'}/{urllib.parse.quote(file_name)}"


def metafile(name, filename, side, download, update=None):
    lines = [f"name = {q(name)}", f"filename = {q(filename)}", f"side = {q(side)}", "", "[download]"]
    lines += [f"{k} = {q(v)}" for k, v in download.items()]
    if update:
        kind, values = update
        lines += ["", "[update]", f"[update.{kind}]"]
        lines += [f"{k} = {v if isinstance(v, int) else q(v)}" for k, v in values.items()]
    return "\n".join(lines) + "\n"


def prism_zip(minecraft, fabric):
    """A Prism Launcher instance that runs packwiz before every launch (Add Instance > Import, this zip's URL)."""
    command = f'"$INST_JAVA" -jar packwiz-installer-bootstrap.jar {PACK_URL}'
    cfg = "\n".join([
        "InstanceType=OneSix",
        "name=Reason",
        "OverrideCommands=true",
        # no ConfigVersion line: Prism then reads this as MultiMC's plain format, where a value is taken as written
        # (quotes kept), as packwiz's own Prism instructions write it
        f"PreLaunchCommand={command}",
        "OverrideMemory=true",
        "MinMemAlloc=2048",
        f"MaxMemAlloc={MEMORY_MB}",
        "",
    ])
    pack = {"formatVersion": 1, "components": [
        {"uid": "net.minecraft", "version": minecraft, "important": True},
        {"uid": "net.fabricmc.intermediary", "version": minecraft, "dependencyOnly": True},
        {"uid": "net.fabricmc.fabric-loader", "version": fabric},
    ]}
    bootstrap = http(BOOTSTRAP_URL)
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in [("instance.cfg", cfg), ("mmc-pack.json", json.dumps(pack, indent=2) + "\n"),
                           (".minecraft/packwiz-installer-bootstrap.jar", bootstrap)]:
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    label = sha("sha256", (cfg + json.dumps(pack, sort_keys=True) + BOOTSTRAP_URL).encode())
    return buf.getvalue(), label


def urls_in(directory):
    """The release asset names an earlier pack's metafiles point at."""
    names = set()
    prefix = f"https://github.com/{REPO}/releases/download/{RELEASE_TAG}/"
    for root, _, files in os.walk(directory or "/nonexistent"):
        for f in files:
            if f.endswith(".pw.toml"):
                for m in re.finditer(r'url = "([^"]+)"', open(os.path.join(root, f), encoding="utf-8").read()):
                    if m.group(1).startswith(prefix):
                        names.add(urllib.parse.unquote(m.group(1)[len(prefix):]))
    return names


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("list")
    ap.add_argument("--out", required=True)
    ap.add_argument("--previous", help="the last published pack, so its assets outlive this run")
    ap.add_argument("--publish", action="store_true")
    args = ap.parse_args()
    spec = json.load(open(args.list, encoding="utf-8"))
    release = Release(args.publish)
    files = {}  # path in the pack -> text
    problems = []

    mods = []
    for m in spec["mods"]:
        data = open(m["file"], "rb").read()
        meta = fabric_meta(data)
        mods.append(dict(m, data=data, meta=meta, sha1=sha("sha1", data)))

    found = modrinth_versions([m["sha1"] for m in mods if m["from"] == "modrinth"])
    used = set()
    for m in mods:
        meta, data = m["meta"], m["data"]
        jar = os.path.basename(m["file"])
        name = meta.get("name") or meta.get("id") or jar
        key = slug(meta.get("id") or jar)
        while f"mods/{key}.pw.toml" in files:
            key += "-2"
        path = f"mods/{key}.pw.toml"
        if m["from"] == "modrinth":
            version = found.get(m["sha1"])
            file = next((f for f in (version or {}).get("files", []) if f["hashes"].get("sha1") == m["sha1"]), None)
            if file is None:
                problems.append(f"{jar}: Modrinth has no file with this SHA-1 ({m['sha1']})")
                continue
            files[path] = metafile(name, file["filename"], m["side"],
                                   {"url": file["url"], "hash-format": "sha512", "hash": file["hashes"]["sha512"]},
                                   ("modrinth", {"mod-id": version["project_id"], "version": version["id"]}))
        elif m["from"] == "release":
            stem, ext = os.path.splitext(jar)
            asset = safe_asset(f"{stem}~{content_id(data)[:12]}{ext}")
            digest = sha("sha256", data)
            # an asset already there with these contents keeps its own SHA-256 (its label), whatever this build's is
            label = release.assets.get(asset) or digest
            release.ensure(asset, data, label)
            used.add(asset)
            files[path] = metafile(name, jar, m["side"],
                                   {"url": release.url(asset), "hash-format": "sha256", "hash": label})
        else:  # curseforge
            files[path] = metafile(name, jar, m["side"], {"hash-format": "sha1", "hash": m["sha1"],
                                                         "mode": "metadata:curseforge"},
                                   ("curseforge", {"file-id": m["cf"][1], "project-id": m["cf"][0]}))

    # resource and shader packs: through CurseForge, as the CurseForge import gets them
    for kind in ("resourcepacks", "shaderpacks"):
        for p in spec.get(kind, []):
            try:
                data = http(cf_url(p["cf"][1], p["file"]))
            except Exception as e:  # noqa: BLE001 - reported below
                problems.append(f"{p['file']}: couldn't download it from CurseForge's CDN to hash it ({e})")
                continue
            # "cdn": a file packwiz can't get through CurseForge's API comes straight from CurseForge's CDN
            download = ({"url": cf_url(p["cf"][1], p["file"])} if p.get("cdn") else {"mode": "metadata:curseforge"})
            files[f"{kind}/{slug(os.path.splitext(p['file'])[0])}.pw.toml"] = metafile(
                os.path.splitext(p["file"])[0], p["file"], "client",
                dict(download, **{"hash-format": "sha1", "hash": sha("sha1", data)}),
                ("curseforge", {"file-id": p["cf"][1], "project-id": p["cf"][0]}))

    if problems:
        print("The tester pack wasn't built:\n  " + "\n  ".join(problems), file=sys.stderr)
        sys.exit(1)

    index = ['hash-format = "sha256"', ""]
    for path in sorted(files):
        index += ["[[files]]", f"file = {q(path)}", f"hash = {q(sha('sha256', files[path].encode()))}",
                  "metafile = true", ""]
    files["index.toml"] = "\n".join(index)
    files["pack.toml"] = "\n".join([
        f"name = {q(spec['name'])}",
        f"version = {q(spec['version'])}",
        'pack-format = "packwiz:1.1.0"',
        "",
        "[index]",
        'file = "index.toml"',
        'hash-format = "sha256"',
        f"hash = {q(sha('sha256', files['index.toml'].encode()))}",
        "",
        "[versions]",
        f"minecraft = {q(spec['minecraft'])}",
        f"fabric = {q(spec['fabric'])}",
        "",
    ])

    if os.path.isdir(args.out):
        shutil.rmtree(args.out)
    for path, text in files.items():
        full = os.path.join(args.out, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)

    if args.publish:
        data, label = prism_zip(spec["minecraft"], spec["fabric"])
        release.ensure(PRISM_ZIP, data, label)
    release.prune(used | urls_in(args.previous) | {PRISM_ZIP})
    print(f"tester pack: {len(files) - 2} files, {len(used)} of them ours, in {args.out}")


if __name__ == "__main__":
    main()
