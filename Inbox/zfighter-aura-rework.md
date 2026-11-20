---
status: writing
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
