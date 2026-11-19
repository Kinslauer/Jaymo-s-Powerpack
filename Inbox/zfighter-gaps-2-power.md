---
status: open
for: hermes
from: claude-cloud-zfighter-checklist-thread
asked: 2026-11-19
claimed_by: 
pr: 
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWJ3UmFH2PavpakbTsfUgXo4
---

# Z Fighter gaps 2: power (holding back, stamina, Limit Break tiers, unlocked potential, fighting styles)

**Andy (2026-11-19):** ticked these rows on the Z Fighter gap checklist (https://claude.ai/artifact/UBvYXGNqtkdh8cu2jVCg1L, rows saved in its db doc picks/andy) and said "done, these can go in the vault". His notes on rows are quoted as **Andy's note**.

**Source:** DragonMineZ (GitHub DragonMineZ/dragonminez, GPL-3.0, Forge 1.20.1). Ideas only: our mods are ARR/MIT, so no copied code, art or Dragon Ball names; every feature gets our own name. Check the "Already in" list on the checklist page before building, so nothing is built twice.

## Picked
- **Holding back your power** (DMZ Power Release): charging raises how much of your power you let out, 0 to 100%; every stat scales with it; a key lowers it to spar, capture alive or knock out without killing.
- **Stamina for dashes, guarding and forms** (DMZ Max Stamina): Flash Step, the guard and going into a form also draw stamina (use the pack's existing stamina if there is one), meditation and rest refill it.
- **More Limit Break tiers** (DMZ Kaioken x2 to x100): three more tiers past the three we have, each costing health every second, only on base or Super.
- **Unlocked potential** (DMZ Potential Unlock, Ultimate): a late rite with a Verdant elder or the Keeper; its state takes your strongest form's strength without that form's look or drain. Lore first: place it in Lore.md and quote it to Andy.
- **Fighting styles with a passive each** (DMZ classes): one Z Fighter style picked once (changed by a wish), on top of Kingdoms classes: fury stacks from unbroken hits, cheaper ki cooldowns, more harm to hurt foes, regen and crits at low health, soaking a friend's harm, healing ki, ki then fists, a tank style.

**Where to start:** ZFighterPower.java (hooks only, see rules), ZFighterInputClient.java, ZFighterTrainingScreen.java, ZFighterWishExtras.java.

## Rules
- One PR, branch `claude/zfighter-gaps-2-power`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Every Minecraft API checked against 26.3.
- No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better"): reuse player-model fighters, MCA villagers and existing mobs.
- The aura task (zfighter-aura-rework) owns ZFighterPower.java, ZFighterKiAura*.java and ZFighterAuraFlame.java while it is open: keep edits there to one-line hooks, or wait for it to merge. The zfighter-gaps tasks all touch Z Fighter code, so build them one after another (Powers merges one at a time), in number order unless Andy says otherwise.
- No slash commands for player features, no discovery chat text (standing rulings). Abilities.md rows for every new input.
- `python3 tools/pr_checks.py` passes; compile check in the cloud.

## Notes
-
