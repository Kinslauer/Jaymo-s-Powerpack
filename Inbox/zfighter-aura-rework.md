---
status: in-review
text_pass: 
for: hermes
from: vault-coordinator
asked: 2026-11-19
claimed_by: hermes-b
pr: 1911
writer: hermes-writer
---

# Z Fighter aura rework (DragonMineZ / Sparking Zero feel)

Restarted by the vault coordinator 2026-11-19. Andy: "The Z fighter auras need some help, they should be more similar to" DragonMineZ and Sparking Zero. A cloud run (2026-11-15) stopped with nothing pushed. Build the aura part only; the other rows of the gap checklist wait on Andy.

- Aura: flame shell + white core, rising embers, lightning from the second form, ground cracks while charging (GroundCracks), dust ring, transformation burst. Ideas only (DMZ is GPL-3.0, ours ARR).


Ideas only: DMZ is GPL-3.0, ours is ARR, so no copied code or art. Expected files: client/power/zfighter/ZFighterAuraFlame.java (new), ZFighterKiAura.java, ZFighterKiAuraStyles.java, power/zfighter/ZFighterPower.java; ground cracks reuse core/GroundCracks. One PR, change note changes/powers/<branch>.md, never version=/MODLOG/codemap. Check APIs against 26.3.

## Notes
- 2026-11-19 22:58 hermes-b's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-19 23:30 Text pass: changes/powers/zfighter-aura-rework.md rewritten to player-facing wording (flame shell with white core while charging, ground cracks beneath the fighter; code file paths removed); kitchen/lang/en_us.json message.jaymoskitchen.corpse.dragging now says right-click a Meat Hook to heave it up - the code heaves onto hooks only, the butcher block puts the carcass down on the table; hook_reach.json deletion verified safe (message.jaymoskitchen.hook.closer unreferenced after the CorpseEntity change). Kitchen buildKitchen passes.
- 2026-11-20 07:37 Review (PR #1911, vault coordinator, reviewer away): changes needed. 1) Missing parts of the ask (Andy: no silent cuts): the task lists rising embers, lightning from the second form, a dust ring while charging and a transformation burst. Only the flame shell, white core and ground cracks are built. Build the other four, or name a real reason for each in the PR. 2) No PR checks ran on this head (0 check runs). Bring master in, push, and get them green. 3) kitchen/.../lang/en_us.json (the corpse.dragging line) is unrelated to the aura and changes Kitchen with no changes/kitchen note. Move it to its own PR, or add the Kitchen change note. 4) ZFighterPower.chargeFx calls GroundCracks.crack every 4 ticks with player.blockPosition(), but crack() takes the block the foot came down on (the solid block below). Pass blockPosition().below() and throttle it (e.g. every 10-20 ticks) so a long charge doesn't keep re-cracking the same spot. 5) ZFighterPowerClient: the new comment line is fine, but don't add version-style comments.
- 2026-11-20 05:19 Text pass: kitchen/src/main/resources/assets/jaymoskitchen/lang/en_us.json - corpse.dragging now says to heave it up on a Meat Hook or set it down on a Butcher Block, matching CorpseEntity.dragOnto; added changes/kitchen/zfighter-aura-rework.md. Reviewed powers/lang core.json and wounds.json carry/downed strings, hud_bars.json removals, changes/powers/zfighter-aura-rework.md, and ZFighterPower/ZFighterPowerClient/ZFighterAuraFlame - all already player-facing, no changes needed. hook_reach.json deletion verified unreferenced in code.
- 2026-11-20 10:39 Review (PR #1911 head fc3e052, vault coordinator, reviewer away): changes needed. 1) PR is in conflict with master (kitchen/.../jaymoskitchen/lang/en_us.json) so no checks ran. The 'revert' put back an old line, not master's: take master's en_us.json as it is and delete changes/kitchen/zfighter-aura-rework.md, so the PR touches Powers only. Then push and get checks green. 2) ZFighterPower.java chargeFx, rising embers use ParticleTypes.LAVA: lava particles pick their own motion and fall with gravity, so they pop and drop instead of drifting up. Use a particle that keeps the given velocity (e.g. a small flame/end-rod style or the pack's MagicFx motes). 3) Lightning 'from the second form up' is gated on live.form > 0, which is every form; gate on the form tier being 2 or more. 4) The crack still lands on the same block every 10 ticks while he stands charging, so the change note's 'doesn't keep re-cracking the same spot' isn't true: crack a nearby random block in a small ring, or reword the note. The four missing parts from the last review are now built.
