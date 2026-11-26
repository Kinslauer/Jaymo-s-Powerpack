---
status: done
task: Reference mod compatibility assessment
branch:
pr:
mods: powers
started: 2026-11-24
owns: []
---
# Reference dinosaurs and Venom head

Andy asks whether the two Desktop Reference Mods jars can fit the pack. Venom head only, no full body; dinosaurs belong on the Verdant planet.

Checked both jar metadata and asset listings. Amber Age 1.2.0 targets Forge 66 and Minecraft 26.3; Symbiote 1.0.1 targets Forge 47 and Minecraft 1.20.1. Both declare All Rights Reserved. Neither is a Fabric drop-in. Model classes and textures are present; no .bbmodel or GeckoLib geometry/animation JSON found. Port or original implementation required, with permission for reuse of the originals. No code or pack changes, build or game test.

## Implementation started
Andy confirmed ownership. Two isolated Claude Sonnet implementation tracks started under the repo parallel-track rule: Venom head only and Verdant dinosaurs. Parent coordinates and reviews; child notes claim implementation files. Source location asked; jar recovery can proceed.

## Notes
- 2026-11-24 13:21 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-26 Andy (Waiting on Andy page): restart it: picked up again as Inbox task codex-reference-dinos-venom-restart.
