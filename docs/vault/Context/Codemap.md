# Codemap

One line per class (path, what it does, main methods), made by `tools/codemap.py`. **Grep these before searching the
code**, then open only what you need. The Release workflow regenerates them after every merge; PRs don't commit
them (they conflicted on every merge). Run `python3 tools/codemap.py` locally if you like, then
`git checkout origin/master -- docs/codemap` before the PR.

- `docs/codemap/powers.md` [GitHub](https://github.com/Kinslauer/Jaymo-s-Powerpack/blob/master/docs/codemap/powers.md)
- `docs/codemap/kingdoms.md` [GitHub](https://github.com/Kinslauer/Jaymo-s-Powerpack/blob/master/docs/codemap/kingdoms.md)
- `docs/codemap/kitchen.md` [GitHub](https://github.com/Kinslauer/Jaymo-s-Powerpack/blob/master/docs/codemap/kitchen.md)

```bash
grep -i 'vampire' docs/codemap/kingdoms.md
```
