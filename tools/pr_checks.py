#!/usr/bin/env python3
# Checks a PR branch against master: version bumps, stale jars, copied item models, shared item models.
"""Checks a branch against master before it merges (run by .github/workflows/pr-checks.yml, or by hand).

    python3 tools/pr_checks.py [base-ref]      (default origin/master)

Fails (exit 1) when:
  1. a mod's code or resources changed but its version in gradle.properties wasn't raised above master's;
  2. a libs/*.jar was added that is older than another jar of the same mod already in libs/
     (PR #1096 added kitchen 0.5.38 next to 0.5.57);
  3. a changed item model (models/item/*.json) is an exact copy of another model (PR #1096's 2.0 "reverse" model
     was the forward model);
  4. a changed item definition (items/*.json) uses exactly the same models as another item (PR #1096 pointed the
     gold, diamond and tempest daggers all at the base dagger's model).

A deliberate exception for 3 or 4: add the file's repo path to tools/pr_checks_allow.txt with a short reason.
"""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODS = [  # (name, properties file, source folders whose changes need a version bump)
    ("Powers", "gradle.properties", ["src/"]),
    ("Kingdoms", "kingdoms/gradle.properties", ["kingdoms/src/"]),
    ("Kitchen", "kitchen/gradle.properties", ["kitchen/src/"]),
]


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True, text=True).stdout


def version_of(text):
    m = re.search(r"^version\s*=\s*(\S+)", text, re.M)
    return m.group(1) if m else None


def vtuple(v):
    return tuple(int(x) for x in re.findall(r"\d+", v or ""))


def allowed():
    f = ROOT / "tools/pr_checks_allow.txt"
    if not f.exists():
        return set()
    return {line.split("#")[0].strip() for line in f.read_text().splitlines() if line.split("#")[0].strip()}


def models_in(node, out):
    if isinstance(node, dict):
        for k, v in node.items():
            if k == "model" and isinstance(v, str):
                out.add(v)
            else:
                models_in(v, out)
    elif isinstance(node, list):
        for v in node:
            models_in(v, out)
    return out


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else "origin/master"
    merge_base = git("merge-base", base, "HEAD").strip()
    changes = [l.split("\t") for l in git("diff", "--name-status", merge_base, "HEAD").splitlines()]
    changed = {c[-1] for c in changes if c[0][0] != "D"}
    added = {c[-1] for c in changes if c[0][0] in "AR"}
    allow = allowed()
    errors = []

    # 1. version bumps
    for name, props, folders in MODS:
        if not any(p.startswith(f) for p in changed for f in folders):
            continue
        try:
            old = version_of(git("show", f"{merge_base}:{props}"))
        except subprocess.CalledProcessError:
            continue
        new = version_of((ROOT / props).read_text())
        if vtuple(new) <= vtuple(old):
            errors.append(f"{name} changed but {props} version is {new} (master had {old}). Raise it to the next "
                          f"free number (check open PRs, they take numbers too).")

    # 2. old jars in libs/
    jars = {}
    for jar in (ROOT / "libs").glob("*.jar"):
        m = re.match(r"(.+?)-(\d[\w.+-]*)\.jar$", jar.name)
        if m:
            jars.setdefault(m.group(1), []).append((m.group(2), jar.name))
    for p in added:
        if not (p.startswith("libs/") and p.endswith(".jar")):
            continue
        m = re.match(r"(.+?)-(\d[\w.+-]*)\.jar$", Path(p).name)
        if not m:
            continue
        newer = [n for v, n in jars.get(m.group(1), []) if vtuple(v) > vtuple(m.group(2))]
        if newer:
            errors.append(f"{p} was added but libs/ already has newer {', '.join(newer)}. Don't add the old jar.")

    # 3. copied item models
    def norm(path):
        try:
            return json.dumps(json.loads(path.read_text()), sort_keys=True)
        except (ValueError, OSError):
            return None

    all_models = {}
    for f in ROOT.glob("**/src/main/resources/assets/*/models/item/**/*.json"):
        all_models[str(f.relative_to(ROOT))] = f
    by_content = {}
    for rel, f in all_models.items():
        by_content.setdefault(norm(f), []).append(rel)
    for rel in sorted(changed):
        if rel in all_models and rel not in allow:
            twins = [o for o in by_content.get(norm(all_models[rel]), []) if o != rel]
            if twins:
                errors.append(f"{rel} is an exact copy of {', '.join(twins)}. A variant (reverse grip, stage, "
                              f"colour) needs its own display transforms or textures.")

    # 4. item definitions sharing one model set
    defs = {}
    for f in ROOT.glob("**/src/main/resources/assets/*/items/*.json"):
        try:
            defs[str(f.relative_to(ROOT))] = frozenset(models_in(json.loads(f.read_text()), set()))
        except ValueError as e:
            if str(f.relative_to(ROOT)) in changed:
                errors.append(f"{f.relative_to(ROOT)} is not valid JSON: {e}")
    for rel in sorted(changed):
        if rel in defs and rel not in allow and defs[rel]:
            same = [o for o, ms in defs.items() if o != rel and ms == defs[rel]]
            if same:
                errors.append(f"{rel} uses exactly the same models as {', '.join(same)}. Each item (or stage) "
                              f"should point at its own model.")

    if errors:
        print("PR checks failed:\n")
        for e in errors:
            print(" - " + e)
        print("\nA deliberate copy or shared model: add its path to tools/pr_checks_allow.txt with a reason.")
        return 1
    print(f"PR checks passed ({len(changed)} changed files against {base}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
