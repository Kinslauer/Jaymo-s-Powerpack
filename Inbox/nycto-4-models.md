---
status: done
for: prometheus
from: claude-cloud (Night's Blood plan thread)
asked: 2026-11-26
claimed_by: hermes-modeller
pr: 2302
thread: hermes-modeller
claim_handbacks: 2
writer: hermes-writer
text_pass: 
---

# Night's Blood 4 models: the true shape of the night

**Plan:** `Picklists/Nycto-Plan.md`, section "4. New gifts", row "A true shape of the night". A gaunt winged horror for a vampire player's fifth shape: our own vanilla-style model and texture, not a wolf, not a bat, not anything from Nycto (All Rights Reserved). Long thin limbs, membrane wings folded on the back, a lean ashen body, red eyes. Needs an idle, walk, leap and strike. Tinted per vampire skin is a bonus.

**Order:** start once Night's Blood 2 is up, so the model is ready when patch 4's code lands.

## Notes
- 2026-11-26 ON HOLD until Night's Blood 2 is in review.
- 2026-11-27 11:01 Andy 2026-11-27: build in the Hunter's Road thread (cmsg_017LSt6rWz6CzxSGvzDd41XWGpfuaQdriP8NJiY6HrB9HX), not by bots. Wave holds unchanged.
- 2026-11-27 11:41 Back to the modeller (Hunter's Road thread, 2026-11-27): models are Prometheus' job; that thread builds the patch once each model lands.
- 2026-11-27 11:43 Opened: nycto-2 is in review (#2288), as the wave plan says.
- 2026-11-27 08:35 Andy 2026-11-27: models to Codex and Antigravity through T3
- 2026-11-27 09:06 t3's claim had no sign of work for 30 min: handed back as open.
- 2026-11-27 09:07 Antigravity quota reached 2026-11-27 (resets about 1:30 PM) partway through. Uncommitted work kept in T3 worktree C:\Users\toopo\.t3\worktrees\Origins Mod\t3-nycto-4-models-17459abb230bddb0 (night_horror bbmodel, geo, animation, 6 textures, gen_night_horror.py, previews). Resume with t3 thread send to mcp:e2a61563 after the reset.
- 2026-11-27 14:56 t3's claim had no sign of work for 30 min: handed back as open.
- 2026-11-27 10:01 Restored (Claude for Andy): a vault sync from the vm at 14:56 UTC merged in a stale copy that still had t3's claim, released it as open for antigravity and dropped these notes. Andy 2026-11-27: Prometheus (hermes-modeller) owns this, run live since 9:35 AM; do NOT resume it in T3/Antigravity. Andy 8:59 AM: bigger wings (Antigravity never finished that; the copied geo still has the old wing sizes, see Agents/nycto-4-models.md).
- 2026-11-27 10:23 hermes-modeller's last text pass stopped before finishing; handed back as in-review.
- 2026-11-27 10:44 Text pass: rewrote changes/kingdoms/nycto-4-models.md (the PR's only player-visible text) to plain player-facing wording — removed em dash, GeckoLib/file-name internals, tooling and Andy-asked-for meta, plus the date; kept the skin swaps, wing changes and a built-not-played line
- 2026-11-27 11:53 Andy 2026-11-27 11:46 AM: fix these two on PR #2302 (same branch claude/nycto-4-models, same PR). (1) Wing speckle: the wing membranes (wing_in/wing_out, paint_wing_membrane in kingdoms/tools/gen_night_horror.py) are speckled with scattered red and black pixels that read as per-pixel noise against docs/Vanilla-Style.md. Repaint them as clean shapes: flat membrane fill from one MAT ramp, lighter on top and darker at the trailing edge, finger-bone struts as clean 1px lines, and at most a few long continuous veins, no isolated single pixels. Do the same on all five skins. (2) Legs read straight in every pose, though the PR says digitigrade. Give them a clear reverse-knee zigzag at rest: thigh angled well forward (about 30-35 deg, now 14), shin swept well back (about -45 to -50 deg, now -19), a longer raised tarsus, so the heel/hock sits off the ground; keep the feet flat on y=0 and re-tune the walk, run, leap and strike leg keys to match. Re-render every preview, re-run python tools/art_audit.py and python tools/pr_checks.py origin/master, push to the same PR, then set status=in-review.
- 2026-11-27 17:02 Taken back from Prometheus 17:02Z: Andy's two fixes (flat wings folded at rest, digitigrade legs) are pushed to #2302 at dfbb0fa83 by the Hunter's Road thread; Andy said merge 17:01Z. Don't rework.
- 2026-11-27 17:17 Merged #2302.
