---
updated: 2026-11-06
---
# Handoff (current summary)

`docs/HANDOFF.md` was written 2026-10-10 at Powers 0.7.1. Its folder layout, build notes and **26.3 API facts** still
hold; its "where things stand" and "next" sections are history. This note is the current picture; versions are on
[[Home]]. [HANDOFF.md on GitHub](https://github.com/Kinslauer/Jaymo-s-Powerpack/blob/master/docs/HANDOFF.md)

## The three mods

- **Root: Jaymo's Powers** (`jaymospowers`). Powers: Gravity, Wind Control, Web Slinger (with the symbiote), Pocket
  Arsenal, Chronokinesis, Resonance, Darktide, Xenoform, and more since. Each power owns `power/<name>` (+ client),
  `lang/parts/<name>.json` and its mixin configs; shared code in `core/` (Wounds, Shakes, Soundscape...) and `api/`.
  The pack version follows Powers.
- **`kingdoms/`: Jaymo's Kingdoms** (`jaymoskingdoms`). Kingdoms, folk, talks, barbarians, vampires, the Hollow and a
  lot more. Lang split in `lang/parts/*.json`, merged at build.
- **`kitchen/`: Jaymo's Kitchen** (`jaymoskitchen`). Needs Cooking for Blockheads and Balm. Its built jar is
  committed in `libs/`; the Powers build picks the one matching `kitchen/gradle.properties`.
- Kingdoms and Kitchen never compile against Powers; they talk through shared scoreboard tags (`jaymospowers_downed`
  etc., listed in `docs/HANDOFF.md`).

## Building

- JDK 25, Gradle wrapper. `./gradlew build` at the root, in `kingdoms/`, in `kitchen/`. On this PC build through the
  PowerShell tool.
- 26.3 is unobfuscated (Mojang names). `./gradlew genSources` for the sources; check every API there.
- `packageModpack` / `serverMods` / `runClient` / `runServer` only make sense on Andy's PC. Deploying to the Steam
  Deck server: the `pbdr` skill.

## Where to look

- [[Codemap]] first, then open only the classes you need.
- [[Logs]] for what changed recently and why.
- [[Plans]] for designs and Andy's answers.
- [[Rules]] for the do's and don'ts.
