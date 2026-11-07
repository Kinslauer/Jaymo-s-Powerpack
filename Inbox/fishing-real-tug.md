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
- 2026-11-07 10:44 Review (head 5f98417): changes needed. 1) Crash: FishingHookMixin.java:40 and :59 cast the hook owner to ServerPlayer, but FishingHook.tick also runs on the client, where the owner is a client player: ClassCastException as soon as anyone's hook sits in water for a second. Return early when hook.level().isClientSide() before any cast. 2) No fish can ever be caught: FishingReal.java:105 adds strength*0.05 tension every tick (cod 0.0175, the weakest), while reeling (:53, REEL_RELEASE 0.015 per packet) takes off less, so tension always climbs; it passes TOO_TENSE 0.6 long before progress reaches 1 (500 reel ticks), then the line snaps. 3) It isn't a tug-of-war: holding use only ever helps, so the answer is always 'hold it'. Andy asked for line tension where pulling too hard breaks the line: reeling should raise tension and pull the fish in, easing off should let tension fall while the fish takes line back; strong fish surge harder. 4) FishingReal.java:182-193 keepNear pulls the fish toward the player, so from a bank it drags the fish onto land, where it dies and :98 reports a snapped line. Pull it toward the hook/water instead. 5) FishingReal.java:98: the fight goes on after the hook is gone (player switches item or the hook is removed); end the fight when the stored hook isRemoved(). 6) FishingRealClient.java:59-60 sizes the bar from getWindow().getWidth()/getHeight() (window pixels): off-screen at GUI scale 2+. Use graphics.guiWidth()/guiHeight() like AimCrosshair.java:274. 7) FishingRealClient.java:68 hard-codes 'Fishing: N% tension': move it to lang/parts/fishing.json. 8) Tuning numbers inline: fish strengths (FishingReal.java:141-146), the 0.05 pull (:105), bite chance 0.03 (:200), keepNear distances (:186-191), fish weights (FishingHookMixin.java:48): make them constants at the top. 9) Conflicts with master and master is already 0.21.51: merge origin/master, take the next free version, keep master's MODLOG and append only this entry. Then build, pr_checks, push, and set this task to status: in-review (that's what the review loop looks for; it was still 'claimed').
