---
status: open
for: hermes
from: tiv (Discord, Questions thread, 2026-11-08)
asked: 2026-11-08
thread: 1569189294512537671
claimed_by: 
pr:
suggested_owns: [src/main/java/com/jaymospowers/power/zfighter/ZFighterItems.java, src/main/java/com/jaymospowers/core/Training.java, src/main/java/com/jaymospowers/core/Meditate.java, kingdoms/src/main/java/com/jaymoskingdoms/Skills.java, kingdoms/src/main/java/com/jaymoskingdoms/Stats.java]
---

# Training weights build Might for everyone, and meditating in them is shadowboxing

Right now the ankle weights only do anything for a Z Fighter. Tiv wants them to be a way for anyone to build Might, gated behind patience rather than power.

## What's wanted

Tiv, in the Questions thread: "okay, they should build might for everyone, wearing them should turn your meditate into shadowboxing. non gravity or z fighters are REALLY slow and bad at it so they should find another way, its only good if you keep at it. as you improve over time like anything else."

- Wearing either pair of training weights turns the Meditate key into shadowboxing, for every player whatever power they hold.
- Shadowboxing in them builds Might (Kingdoms' Might attribute), not only a Z Fighter's Training Points.
- For a player who is neither Gravity nor a Z Fighter it is meant to be bad: very slow and weak returns, so those players are pushed to find another way.
- It only pays off through sustained use; improvement grows over time like every other kind of training in the pack.

## Notes

- Where it stands: `ZFighterItems.weightRate` feeds a Z Fighter's training only (1.5x light, 2x heavy, stacking with the Weighted Gi). Kingdoms' Strength skill xp already takes a bonus from the Weighted Gi on the chest (`Skills.grant`, `GI_STRENGTH`, 0.7.464) but never from the ankle weights. Might itself is an attribute bought with skill-tree points (`Stats`), so the weights will have to hand points over directly. Check `docs/ai-bundle/memory/training-pass.md` and `attributes-heritage.md` before building.
- The Meditate key is `core/Meditate`, which every player has, and the shadowboxing pose already exists for Z Fighters (`Training.spot` returns "weighted" for gravity and zfighter only). Turning it into shadowboxing for everyone is the same hook with the power gate removed.
- "Really slow and bad at it", settled by Tiv: a non-Gravity, non-Z-Fighter always gets a tenth of what a Z Fighter would get from the same session, and the Might lands as points directly (not through Strength xp) — about one point an hour. At the current per-point value (0.375% attack damage each, Might capped at 100) that is a hundred-odd hours to the cap, which is the intended "only good if you keep at it" grind. Gravity and Z Fighters keep their own faster path.
- Cross-mod: Might lives in Kingdoms (`Stats`, `Skills`), the meditate/shadowboxing hook in Powers core. The rule is one PR per project, so this is likely two linked PRs — the Powers half reaching Kingdoms' Might the way `KitchenCompat` already does, rather than importing across mods.
- Both items already exist, so there is no new item to clear with Andy first.
- 2026-11-09 21:12 hermes-b's last run stopped before finishing; handed back as open so it can carry on.
