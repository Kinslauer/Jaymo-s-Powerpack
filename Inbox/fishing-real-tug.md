---
status: ready
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
- 2026-11-07 11:52 Review (head a365851): changes needed. Fixed since the last round: the client-side crash, the hook-removed check, keepNear toward the hook, the HUD size and its lang key. Still to do: 1) Startup crash: FishingHookMixin.java:66 injects into FishingHook.reelIn, which doesn't exist in 26.3 (FishingHook.java has tick, catchingFish, retrieve(ItemStack) :440, pullEntity :502). With defaultRequire 1 in jaymospowers.fishing.mixins.json the required mixin fails to apply and the game won't start. Inject into retrieve(ItemStack) (it returns int: use CallbackInfoReturnable<Integer>, setReturnValue(0)). 2) Reeling ends the fight: the reel control is holding use (FishingRealClient.java:46 keyUse), and a use click with a rod calls FishingRodItem.use -> hook.retrieve, which discards the hook; FishingReal.java:111 then ends the fight. Fixing 1 (cancel retrieve while fighting) fixes this too. 3) Every fight ends on its first tick: progress starts at 0 (FishingReal.java:170) and :150 ends the fight as 'got away' when progress <= 0, and the player can't be holding use yet. Start progress at a constant (e.g. START_PROGRESS = 0.3F) or only let it get away after MIN_FIGHT_TICKS. 4) Tropical fish and pufferfish can never be caught: at the best possible rhythm (hovering at TOO_TENSE 0.8) progress gains about 0.00084/tick for tropical (strength 0.6) and 0.00025/tick for pufferfish (0.75), so they need 1190 and ~4000 ticks, past MAX_FIGHT_TICKS 900. Andy asked for stronger fish to fight harder, not to be uncatchable: retune (e.g. lower PROGRESS_FISH_BACK, or raise MAX_FIGHT_TICKS) so every fish is catchable with good timing, and check the numbers for each fish. 5) Vanilla still fishes alongside: FishingHookMixin only cancels retrieval while a fight is on, so a normal vanilla bite reeled in before our bite still gives vanilla loot and treasure. Andy wants fishing changed to real fish: stop vanilla's own bite/loot (e.g. cancel catchingFish) so every catch is a fight. 6) Tuning numbers still inline: fish strengths FishingReal.java:164-169, keepNear 3.0/0.12/0.05 :211-213, fish kind weights FishingHookMixin.java:51. 7) Version: master is already 0.21.52 (#1116), the same number this branch took. Merge origin/master, take 0.21.53, keep master's MODLOG and append only this entry; GitHub still shows the PR as conflicting, so no checks have run. Push, wait for green checks, then set status=in-review.
- 2026-11-07 12:21 Review (head c8fa200): pass. A bite now hooks a live cod, salmon, pufferfish or tropical fish and starts a tug-of-war: hold use to reel (tension climbs with the fish's strength, the line snaps at full), ease off to let tension fall while the fish takes line back; every fish is catchable with good timing (pufferfish slowest, about 760 ticks), and vanilla fishing loot is gone. Not played in game: the feel of the tuning numbers and the hooked fish's movement near the hook are untested. Small nit for later: KEEP_NEAR_* constants sit mid-file (FishingReal.java:214) rather than at the top.
- 2026-11-07 12:21 Reviewed; Andy allowed sending it to the coordinator.
