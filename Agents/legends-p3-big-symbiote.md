---
status: running
task: legends-p3-big-symbiote
branch: hermes/legends-p3
pr: 
mods: powers
versions: 
started: 2026-11-27
owns:
  - src/main/java/com/jaymospowers/power/web/WebBruteArts.java
  - src/main/java/com/jaymospowers/power/web/WebBruteMovement.java
  - src/main/java/com/jaymospowers/power/web/WebSymbiote.java
  - src/client/java/com/jaymospowers/client/power/web/WebBruteClient.java
---
# legends-p3-big-symbiote

**Task:** Patch 3 big symbiote (Hulk rework): apply the 5 remaining review fixes on top of codex/legends-p3 and open a PR against master — non-clashing default keys for the 7 brute arts, wall-climb only when facing the wall, end a move when its own ticks end while stones keep flying, move 3 displaced javadocs back to their members, and close the Patch 3 plan gaps (rubble steer at aimed foe + breakGround, upheave slabs tear via breakGround, quake boulders fall on foes).

**Plan**
- Fix default keys (WebBruteClient.DEFAULTS): escape Numpad 2-8 clashes
- Fix wall-vs-leap (WebBruteMovement.wall): require facing the wall within ~60 deg
- Fix stones flying (WebBruteArts): move ends when its ticks end, stones finish on a FLYING list
- Fix 3 javadocs (WebSymbiote): struggle bar -> STRUGGLE, hands control back -> endTakeover, big body off -> shedRedBody
- Plan gaps: rubble steer, upheave breakGround, quake boulders on foes; hk_losing math verified
- Build compileClientJava + compileJava, run tools/pr_checks.py
- Commit, push, open PR vs master, set status=in-review

**Notes**
- Branch from origin/codex/legends-p3 (head 006772254b); PR base is master
- hk_losing: on-beat press adds BEAT_PRESS 4.0F fading at BEAT_FADE 0.035/tick vs mash +1.0F fading at 0.18/tick — beat wins control back far faster; verified, reported in PR
- Not tested in game; compile-checked only

**Done**
What a player will see, and what wasn't tested.
