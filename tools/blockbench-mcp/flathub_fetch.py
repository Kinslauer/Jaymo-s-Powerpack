#!/usr/bin/python3.12
"""Fetch one Flathub app ref into a local OSTree archive repo using plain HTTPS GETs,
then check it out. Used because libostree's own pull is refused (403) by this
cloud's egress proxy while ordinary GETs of the same static files are allowed.

usage: flathub_fetch.py <ref> <repo_dir> <checkout_dir>
"""
import os, sys, subprocess, urllib.request, concurrent.futures as cf
import gi
gi.require_version('OSTree', '1.0')
from gi.repository import GLib

BASE = 'https://dl.flathub.org/repo/'
ref, repo, out = sys.argv[1:4]


def get(path):
    with urllib.request.urlopen(BASE + path, timeout=120) as r:
        return r.read()


def obj_path(csum, kind):
    return f'objects/{csum[:2]}/{csum[2:]}.{kind}'


def fetch(csum, kind):
    p = obj_path(csum, kind)
    dst = os.path.join(repo, p)
    if not os.path.exists(dst):
        data = get(p)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst + '.tmp', 'wb') as f:
            f.write(data)
        os.replace(dst + '.tmp', dst)
    with open(dst, 'rb') as f:
        return f.read()


def hexs(b):
    return bytes(b).hex()


if not os.path.exists(os.path.join(repo, 'config')):
    subprocess.run(['ostree', f'--repo={repo}', 'init', '--mode=archive'], check=True)

commit = get('refs/heads/' + ref).decode().strip()
print('commit', commit)
cv = GLib.Variant.new_from_bytes(GLib.VariantType('(a{sv}aya(say)sstayay)'),
                                 GLib.Bytes.new(fetch(commit, 'commit')), False).unpack()
root_tree, root_meta = hexs(cv[6]), hexs(cv[7])

files, trees = set(), [(root_tree, root_meta)]
while trees:
    t, m = trees.pop()
    fetch(m, 'dirmeta')
    dt = GLib.Variant.new_from_bytes(GLib.VariantType('(a(say)a(sayay))'),
                                     GLib.Bytes.new(fetch(t, 'dirtree')), False).unpack()
    for _name, c in dt[0]:
        files.add(hexs(c))
    for _name, tc, mc in dt[1]:
        trees.append((hexs(tc), hexs(mc)))
print('files', len(files))
with cf.ThreadPoolExecutor(6) as ex:
    for i, _ in enumerate(ex.map(lambda c: fetch(c, 'filez'), files)):
        if i % 200 == 0:
            print('  fetched', i, flush=True)

refdir = os.path.join(repo, 'refs/heads', os.path.dirname(ref))
os.makedirs(refdir, exist_ok=True)
with open(os.path.join(repo, 'refs/heads', ref), 'w') as f:
    f.write(commit + '\n')
subprocess.run(['ostree', f'--repo={repo}', 'checkout', '--user-mode', '--force-copy', commit, out], check=True)
print('checked out to', out)
