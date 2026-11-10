---
status: ready
task: astronomy-sky
branch: claude/astronomy-sky, claude/reincarnation-wipe-fixes
with: 
pr: 1193, 1205
mods: kingdoms
versions: 
started: 2026-11-09
owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/astronomy/SkyClient.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/astronomy/Discovery.java
  - kingdoms/src/main/java/com/jaymoskingdoms/astronomy/StarDiscovery.java
  - kingdoms/src/main/java/com/jaymoskingdoms/McaBridge.java (forgetPlayer and below)
  - E:/hermes/powerpack/vault.py (refuse_red)
  - E:/hermes skills/minecraft/minecraft-modpack-workflow/SKILL.md (all copies: API rule)
---
# Reviewer takes over: night sky, reincarnation follow-up, red-build gate

**Task:** Andy (2026-11-09): fix what #1201 left (wipe order, villagers in unloaded areas), stop Hermes handing in PRs
built on invented APIs, and take over #1193 (night sky) after its third red round.

## Plan
- #1205: villagers really forget (MCA's getMemories() is a copy), unloaded villagers forget on load, wipe before
  MCA's creator, tags and indentation.
- vault.py `refuse_red`: `--set <task> status=in-review` waits for the PR's checks and refuses a red one (exit 4).
- Skill rule "Never guess an API" in every profile copy; stale "bump version" rule replaced by the change-note rule.
- #1193: server-side discovery (StarDiscovery attachment + Looked payload, checked: overworld, night, open sky, rate
  limited), client Discovery reads the synced list, SkyClient from round one with pollution per tick, vectors hoisted,
  heightmap horizon profile so far terrain hides stars, look tick actually registered.

## Notes
- Round one never registered SkyClient.tick, so no star could be discovered at all.
- ArmillaryScreen uses getGameTime() % 24000 for the sky time while the sky uses getOverworldClockTime(): left alone
  (armillary's own task), worth a follow-up.

## Done
Both green on GitHub. #1205: villagers really forget a reborn player (loaded now, unloaded on next load), wipe before MCA's creator. #1193: real night sky with server-side discovery and stars hidden behind far terrain. vault.py refuses red hand-ins (tested on #1193's old head: exit 4). Not tested in game.
