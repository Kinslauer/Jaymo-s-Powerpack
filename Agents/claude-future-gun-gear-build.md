---
status: in-review
task: future-gun-gear
branch: implement/future-gun-gear
with: 
pr: 1671
mods: powers
versions: 
started: 2026-11-17
owns:
  - src/main/java/com/jaymospowers/armory/Armory.java (gun gear items only)
  - src/main/java/com/jaymospowers/armory/TaczGuns.java
  - src/main/java/com/jaymospowers/armory/GunGear*.java
  - src/main/java/com/jaymospowers/power/chrono/future/
  - src/main/resources/assets/jaymospowers/lang/parts/armory.json (gun gear keys only)
  - docs/Future-Gun-Gear-Spec.md
---
# claude-future-gun-gear-build

**Task:** [[Inbox/future-gun-gear]]: rebuild PR #1671 to match Andy's answers (2026-11-17, Decisions.md): tracers both
ways (1 in 4 ordinary shots, tracer rounds always), magazines at the spec sizes, plate carrier, all three together,
loot only from the future's dead and echoes.

## Plan
- Read TaczGuns, FutureGuns, MuzzleFlash, ArsenalFutureGun, Lore.md's future.
- Rework the branch: real tracer streak and light, magazine reload, carrier with its own look, loot drops, no recipes.
- Build, pr_checks, independent verifier, push, set the task in-review.

## Notes
- Takes over from hermes-b's agent note future-gun-gear (blocked on Andy's answers, now given).

## Done
### 2026-11-17: PR #1671 rebuilt (head 8344bebe6d, CI green)
- Tracers 1 in 4 plus tracer rounds; a magazine per gun at its real capacity, snipers included; plate carrier with
  its own art; loot only. Lore.md (new) paragraph "The last stand's kit".
- Not played in game; couldn't build in the cloud (Modrinth CDN blocked), CI built it.
- Andy kept both reloads and the flat icons (2026-11-17); #1669 closed.
