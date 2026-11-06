---
name: gravity-0825
description: Gravity 0.8.25 rework (PR #21) status and the earthboard unlock decision made on Andy's behalf
metadata:
  type: project
---

PR #21 MERGED 2026-10-11 (master now Powers 0.8.25, untested in game; #20 Chrono still uses 0.8.24 and will need a bump): Lift 60 blocks + crosshair mark, G Well shockwave bubble with lingering debris lumps, kneeling poses (GravityPower.KNEEL), earthboard rework, meteor wreck debris. Built, not playtested. Test jar: /mnt/project-files/gravity/.

The earthboard now works for anyone with Weightless unlocked: hold V to ride, and it toggles once mastered, ascended or corrupted. Before this it needed ascension or corruption. I made that call because Andy's hold-until-mastered request otherwise did nothing, and I asked them to confirm.

**Why:** If Andy says to keep the board locked behind ascension, put the gate back in GravityPower.onKey and in GravityRockFlight.toggles.
**How to apply:** Check the thread for Andy's answer before changing the board's unlock again. See [[cloud-playtest-setup]].

PR #28 MERGED 2026-10-12 as Powers 0.8.40 (master now 0.8.40): G is unaimed and kneels everything it touches (no pull). B tap = force kneel within 5 blocks, plus a ~14-block launch if your Well is up. B held = Crush at the aim point; Collapsing Horizon's held B rains the ring on the target. The meteor carves through blocks as it falls. Web/Gravity F5 = first person or over-the-shoulder view only. Untested in game.
