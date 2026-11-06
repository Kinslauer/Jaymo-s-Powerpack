---
name: screen-shake
description: Our own directional screen shake (Perception rejected); PR #157 system + PR #191 every-impact shakes scaled by body heft
metadata:
  type: project
---
2026-10-14 Andy asked to use Perception (Modrinth) for all screenshakes. Not possible: newest build 1.21.11, All Rights Reserved. Andy picked "build ours".
PR #157 MERGED (pack 0.10.34): core/Shakes (server send/around/heavy with a source point) + client/ScreenShake (directional kick then rattle, stacking, explosions shake too).
2026-10-14 22:26Z Andy: "all impacts should have some kind of screenshake, more for bigger impacts like symbiote or large xenos". PR #191 MERGED 2026-10-14 22:51Z (pack 0.10.94, unplaytested): core/Impacts (heft = standing w*h vs a player, x1.4 symbiote suit; landings, hits taken/dealt, heavy footsteps via mixin/ImpactShakeMixin), Shakes.heavy scales by sqrt(heft) up to 1.6 (Shakes.felt = unscaled), Debris.smash shakes, ~60 ability spots added.
**How to apply:** any new shake goes through Shakes/ScreenShake, never a new system; heavy-body moves use Shakes.heavy so size scaling is automatic.
