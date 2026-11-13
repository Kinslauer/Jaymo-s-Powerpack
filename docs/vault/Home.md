---
updated: 2026-11-06
---
# Jaymo's mods: home

Start here. Agents: read this note, then [[How agents use this vault]], then [[Board]].

## Current versions (master)

| Mod | Version | File |
|---|---|---|
| Jaymo's Powers (and the pack) | **0.22.106** | `gradle.properties` |
| Jaymo's Kingdoms | **0.7.801** | `kingdoms/gradle.properties` |
| Jaymo's Kitchen | **0.5.127** | `kitchen/gradle.properties` |

Minecraft 26.3, Fabric, JDK 25. The Release workflow keeps this table, the versions and the MODLOGs up to date after
each merge; PRs add a change note instead (`changes/README.md`) and never take version numbers.

## Merged lately (2026-11-06)

- #1100 Fewer water mobs at once (Kingdoms 0.7.644, Powers 0.21.47)
- #1097 Up to 3 weapons on the body; the tool belt is a bauble and shows them all
- #1098 Dying mid-turn rises you turned: the Hollow's blight and vampire bites
- #1099 Fix Sora's Dagger stages broken by #1096; PR checks Action added
- #1096 Sora's Dagger 2.0 models
- Archer hood joins a fitted cloak (0.21.41); sliding down the wall when the grip goes (0.21.40)
- AI handbook and bundle for other assistants (`docs/AI-HANDBOOK.md`, `docs/ai-bundle/`)

Full detail: the ends of the MODLOGs, see [[Logs]].

## Open PRs

See [[Open PRs]]: every open PR with its branch, last update, checks and the agent working on it. It refreshes by
itself (at most once a minute) when a Claude session starts, when an agent edits a vault note, and when you open the
vault with `tools/open-vault.bat`. To refresh by hand: `python tools/vault_prs.py`.

## What's next

- Whatever Andy asks for next from playtests; each request becomes one agent on the [[Board]].
- #766 waits on Andy's review.
- Open issues to remember (from `docs/AI-HANDBOOK.md` section 9): the invisible orc (#988, grab `logs/latest.log` if
  it happens again); takeovers still drag the camera toward targets; the Aether sky hide on the minimap is unconfirmed.
- `docs/Plan-0.8.md` (Gravity, Arsenal, Chrono, Web) is **done**: built on `plan-0.8` and merged long ago. Keep it as
  the example of how to split a patch into tracks.

## Running agents

See [[Board]]. At most **three** at once, each owning its own files.

## Context

- [[Rules]]: `CLAUDE.md`, the AI handbook, Andy's rulings
- [[Handoff]]: folders, building, 26.3 API facts (current summary; `docs/HANDOFF.md` is from Powers 0.7.1)
- [[Logs]]: MODLOGs and the archive in `docs/modlog/`
- [[Codemap]]: where each class lives
- [[Plans]]: the plan and spec docs
- [[Decisions]]: every decision Andy makes, newest last
