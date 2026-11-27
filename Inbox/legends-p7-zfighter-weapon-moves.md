---
status: ready
for: gemini
from: Andy (Claude thread Rework webs and symbiote from Legends Superheroes, 2026-11-26)
asked: 2026-11-26
claimed_by: 
pr: 2255
text_pass: 
---

# Legends patch 7: Great Ape moves, staged beams, instant step, stacking power-up, chain blades, cross slash

Andy, 2026-11-26: "have codex and gemini take a patch each then check it afterward". This is Gemini's patch.

Plan, licences and rules: `docs/Legends-Plan.md` on branch `claude/project-thread-aaw9tm` (`git fetch origin claude/project-thread-aaw9tm && git show FETCH_HEAD:docs/Legends-Plan.md`). Read its Licences and Rules sections first. Work from master on your own branch and worktree, one PR, change note `changes/powers/<branch>.md`. The Legends add-ons are All Rights Reserved: ideas only, our own code, poses and names. Legends Mod Core is LGPL-3.0: its files may be ported with its notice; a copy is in the Claude project files (legends-study/core-lgpl/), and the jar is https://cdn.modrinth.com/data/TfKZ50GD/versions/kCUYqPqz/Legends-1.20.1-ss1.0.25.jar (assets/legendsmod/legends_animations/player.animation.json, animations/item/katana.animation.json, fx/*.fx). Our player animation library is zigythebird Player Animation Library (PalAccess); it plays Bedrock-format animations. Build to check (compileClientJava), run tools/pr_checks.py, never merge. When the PR is up, set status=in-review pr=<n>: the Claude thread "Rework webs and symbiote from Legends Superheroes" reviews it afterwards (Andy: "have codex and gemini take a patch each then check it afterward").

### Patch 7: Z Fighter and weapon moves

Power: Z Fighter, weapons.

- **Great Ape moves** (`ot_ape`, Anime, M): A full set for the giant ape: swipes left and right, a roar, a ground slam, a leap smash and a boulder throw, each with its own pose. Our side: ZFighterApe already grabs, carries and slams. This fills in swipes, the boulder throw and the leap smash.
- **Staged beam firing** (`ot_beam`, Anime, S): Beams go in stages: gather in the hands, hold, fire, keep firing, end. Foes inside a beam you're holding are outlined red so you can see you're on them. Our side: ZFighterBeams and the beam clash exist. This adds the red outline and smoother stages.
- **Instant step** (`ot_transmit`, Anime, S): Two fingers to the brow and you're a few blocks ahead in a flash, or behind the foe in your sights if you sneak. Our side: A Z Fighter move once ki sense is learned. A short shimmer where you left and where you land.
- **Stacking power-up** (`ot_kaio`, Anime, M): Hold to raise a power-up by stages, a red aura growing each time. Above half, it hurts you every few seconds; letting go keeps it for a while, then leaves you slowed. Our side: A Z Fighter form boost, on ZFighterKiAura's aura with its own colour. Our own name for it.
- **Chain blades** (`ot_chain`, Misc, M): Chained blades that pull a foe to you, and a spinning cyclone of them all around you. Our side: Our chain weapons (the chain weapons models) could take both moves.
- **Cross slash** (`ot_xslash`, Power Rangers, S): Dash through a foe drawing an X across them; a moment later it bursts. Our side: A Weapon Forms move for swords.

## Notes
- 2026-11-26 21:36 gemini's claim had no sign of work for 30 min: handed back as open.
- 2026-11-27 01:43 Reviewer (Claude thread Rework webs and symbiote, 2026-11-27): PR #2255 builds and pr_checks pass; pass after these fixes, push to the same PR then set status=in-review. 1) Lash.chainPull (from land/pullOut) yanks any struck() target at 2.4 b/t: gate on holdable() (no Dragon/Wither) and use the existing YANK heft/liftable weight test so heavy foes pull you or barely move. 2) Great Ape lost R = charge ki and Sneak+R = lower power (master let ABILITY_1 past the ape block); keep those, move the roar to a free ape combo. 3) Base beam sustain (SUSTAIN_BASE_TICKS 16, sustainStrike resetting i-frames at 0.28x every 5 ticks) gives every beam ~+84% free damage incl. bosses and delays the burst: the free base sustain must deal no damage; only V-held sustain may strike, and it costs ki. 4) Instant Step replaced Flash Step on B while Ki Sense is on and on Sneak+B once learned: keep Flash Step on its old keys; Instant Step only when a sensed target is picked (or a free combo), and fall through to Flash Step when Instant Step can't start. 5) tryStart spends Flash Step stamina before the ki check and landing check: spend only when the step happens. 6) Cyclone tooltip says 'charged' but uses the shared SWEEP_MIN_CHARGE: word it to match. Note every remaining deviation in the change note.
- 2026-11-26 21:00 Review (head edc08fd): changes needed. CI green; Overburn, cross slash, red outline, change notes, lang and licence are fine. Fix:
1. ZFighterPower.java:869 - Andy, 2026-11-26: R should still charge ki. Restore the master ape R behaviour (R hold charges ki, Sneak+R = ZFighterRelease.lower; exclude ABILITY_1 from the ape branch again) and move the roar to a free combo (e.g. Sprint+R; check it clashes with nothing). Say the roar's key in the change note and lang.
2. ZFighterLineage.java:733 - ApeMoves.tick runs after the crippled/ApeGrab early returns, so a held boulder freezes mid-air during a grab and a leap smash fires late after a cripple. Tick it before those returns, or refuse a grab/cripple-start while a boulder or leap is active and call ApeMoves.clear in cripple() and on grab start.
3. ApeMoves.java:288 - LEAP_START/BOULDERS survive death and power loss, so a phantom leap smash fires at the next transformation. Call ApeMoves.clear in ZFighterPower.onLost/onDowned/forget (next to InstantStep/Overburn), use endApe in onLost, and gate ApeMoves.tick on apeSize.
4. ZFighterWaveBeam.java:379 - holding V never extends the beam (only a re-press after it is out). Seed Wave.holding from V's state in fire(), so keep-firing works while held.
5. ZFighterWaveBeam.java:152 - a beam in STAGE_END still counts as live for BeamClash, so an invisible beam can lock a clash. Ignore STAGE_END/burstDone waves in the active predicate and Shape.reach.
6. ZFighterInstantStep.java:83 - the early spend() charges ki (Earthling HP, Frost reserve) and then never queues the step; androids under 12 ki can never step. Drop it; let execute() be the one spend (show a no-ki message without spending if wanted).
7. ZFighterPower.java:963 - Divine, Verdant and legend-form fighters never reach the Sneak+B behind-the-foe step (their own Sneak+B returns first). Try the step first when Ki Sense is on and a foe is sighted, else fall back to their move.
8. Lash.java:1960 - Cyclone/chain-pull hits set off AirCombo's launcher and slam. Skip AirCombo during those hits (flag like FallingAttacks.landing) or use Lash's own damage source.
9. Lash.java:1637 - Cyclone's 80-tick cooldown is dodged by switching hotbar slots. Put the cooldown on the axe when the Cyclone starts.
- 2026-11-27 00:02 Review (head 178cb19): pass. All 9 first-round findings and 2 follow-ups fixed and re-checked; CI green. Players get Great Ape swipes, Sprint+R roar (R still charges ki), slam, leap smash and boulder throw; staged beams with a red outline on held targets; Instant Step; Overburn; Chain Axe pull and Cyclone; Cross Slash form. Not tested in game.
- 2026-11-27 00:02 Reviewed; Andy allowed sending it to the coordinator.
- 2026-11-27 10:25 Claude thread finished the review fixes itself (Andy: 'finish both here'), 3b645f8367 + ff4dc22bab; CI green, ready to merge.
