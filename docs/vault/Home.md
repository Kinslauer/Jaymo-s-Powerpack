---
updated: 2026-11-19
---
# Jaymo's mods: home

Start here, then [[Dashboard]]. Agents: read this note, then [[How agents use this vault]], then [[Board]].

## Current versions (master)

| Mod | Version | File |
|---|---|---|
| Jaymo's Powers (and the pack) | **0.25.86** | `gradle.properties` |
| Jaymo's Kingdoms | **0.11.18** | `kingdoms/gradle.properties` |
| Jaymo's Kitchen | **0.6.62** | `kitchen/gradle.properties` |

Minecraft 26.3, Fabric, JDK 25. The Release workflow keeps this table, the versions and the MODLOGs up to date after
each merge; PRs add a change note instead (`changes/README.md`) and never take version numbers.

## What's going on now

See [[Dashboard]]: what waits on Andy (PRs to merge, questions), what's stuck, and the merged work nobody has played
yet. The sync rebuilds it every time, so it never goes stale; tick a line under "Merged, not played yet" once you've
played it. Problems in the live notes are on [[Health]], last week's summary is in `Live/Weekly/`, every task is on
the [[Kanban]], and every open PR is in [[Open PRs]] (`python tools/vault_prs.py` refreshes it).

What's merged, newest last: the ends of the MODLOGs ([[Logs]]).

Open issues to remember (from `docs/AI-HANDBOOK.md` section 9): the invisible orc (#988, grab `logs/latest.log` if it
happens again); takeovers still drag the camera toward targets; the Aether sky hide on the minimap is unconfirmed.
`docs/Plan-0.8.md` (Gravity, Arsenal, Chrono, Web) is the example of how to split a patch into tracks.

## Running agents

See [[Board]]. At most **three** at once, each owning its own files.

## Context

- [[Rules]]: `CLAUDE.md`, the AI handbook, Andy's rulings
- [[Handoff]]: folders, building, 26.3 API facts (current summary; `docs/HANDOFF.md` is from Powers 0.7.1)
- [[Logs]]: MODLOGs and the archive in `docs/modlog/`
- [[Codemap]]: where each class lives
- [[Plans]]: the plan and spec docs
- [[Glossary]]: Andy's words for things and what they're called in the code
- [[Decisions]]: every decision Andy makes, newest last
