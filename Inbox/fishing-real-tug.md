---
status: claimed
for: hermes
from: coordinator
asked: 2026-11-07
thread: Hermes "Main Workflow" chat (stopped by NVIDIA rate limits; work handed off as draft PR #1114)
claimed_by: hermes
pr: 1114
suggested_owns:
  - src/main/java/com/jaymospowers/fishing/
  - src/main/resources/jaymospowers.fishing.mixins.json
  - src/main/resources/assets/jaymospowers/lang/parts/fishing.json
  - src/main/resources/fabric.mod.json (one line: the mixin config)
  - src/main/java/com/jaymospowers/JaymosPowers.java (init call + registerAttachments line)
---
# Realistic fishing with a tug-of-war reel

**Andy's words:** "curseforge.com/minecraft/mc-mods/fishing-real this mod changes fishing in a realistic way that i
want to add. the idea is just to have real fish bite your lure, and i need a tug of war type minigame to reel them in.
involving line tension and stronger fights for stronger fish, meaning they can break your line."

**What's wanted:** a real fish bites your lure. Reeling it in is a tug-of-war: line tension shown on screen, stronger
fish fight harder, and pulling too hard breaks the line. Our own code and art, not copied from Fishing Real.

**Where to start:** draft PR #1114, branch `claude/fishing-real-tug`. Your earlier worktree `E:/wt/fishing-real-tug`
is still on that branch: `cd "E:/wt/fishing-real-tug" && git pull`. It has one WIP commit (`com.jaymospowers.fishing`:
`FishFight`, `FishingPayloads`, `FishingReal`, `mixin/FishingHookMixin`, `jaymospowers.fishing.mixins.json`), never built.
Still missing:
- `FishingReal.init()` is never called.
- The fight attachment isn't listed in `JaymosPowers.registerAttachments()`.
- The mixin config isn't in `fabric.mod.json`.
- No lang keys.
- No client code sends the reel input packet, and there's no tension HUD.
- Version bump and MODLOG entry, taken right before review.

Then the full `sh ./gradlew build` and `tools/pr_checks.py`, then mark the PR ready (`gh pr ready 1114`) with a "not
tested in game" line in its body.

## Notes
- 2026-11-07 coordinator: handed back to Hermes at Andy's request ("hermes can pick it back up with the new reasoning").
