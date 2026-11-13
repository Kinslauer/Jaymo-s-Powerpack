# no more armour-look errors on every launch
- The heavenly, kintsugi, primal and reverent armour sets had equipment look files with no layers, which 26.3 rejects with "Map must have contents" in the log on every launch. Those sets are drawn by their own models, so the files are gone; the game draws no extra layer for them either way.
- `tools/check_data_refs.py` (PR checks) now fails a PR with any data or asset JSON that doesn't parse, or an equipment look with no layers.
- Built; not played.
