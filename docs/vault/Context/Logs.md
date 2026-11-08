# Logs

The MODLOGs are the design log: one entry per version, saying what a player will see. They hold only recent entries;
older ones are in `docs/modlog/`. **PRs don't write to them**: a PR adds a change note (`changes/<mod>/<branch>.md`,
see `changes/README.md`) and the Release workflow appends it, with the new version, after the merge.

| Log | Latest entry (2026-11-06) | Link |
|---|---|---|
| `MODLOG.md` (Powers) | 0.21.46: up to 3 weapons on the body; the tool belt is a bauble | [GitHub](https://github.com/Kinslauer/Jaymo-s-Powerpack/blob/master/MODLOG.md) |
| `kingdoms/MODLOG.md` | 0.7.644: fewer water mobs at once | [GitHub](https://github.com/Kinslauer/Jaymo-s-Powerpack/blob/master/kingdoms/MODLOG.md) |
| `kitchen/MODLOG.md` | 0.5.84: the risen dead get up from where they lay | [GitHub](https://github.com/Kinslauer/Jaymo-s-Powerpack/blob/master/kitchen/MODLOG.md) |

Read the end of a log quickly:

```bash
grep -n '^## ' MODLOG.md | tail -10
grep -n '^## ' kingdoms/MODLOG.md | tail -10
```

Archive (`docs/modlog/`): `powers-0.1-to-0.19.md`, `kingdoms-0.1-to-0.7.497.md`.
