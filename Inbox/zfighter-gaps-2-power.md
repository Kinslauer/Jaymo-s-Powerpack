---
status: writing
for: hermes
from: claude-cloud-zfighter-checklist-thread
asked: 2026-11-19
claimed_by: hermes-b
pr: 1938
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWJ3UmFH2PavpakbTsfUgXo4
writer: hermes-writer
text_pass: 
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
- 2026-11-20 05:29 Text pass: changes/powers/zfighter-gaps-2-power.md rewritten to player-facing wording (removed the javadoc {@link} and the dev-only hooks-ownership and waiting-rows bullets; kept the stamina bullet and 'Built; not played in game.'). New lang part lang/parts/zfighter_stamina.json has one string - message.jaymospowers.zfighter_stamina_empty 'Running on empty: your stamina is spent.' - shown when a stamina cost can't be paid; wording kept, it's plain and brief. ZFighterGuard/ZFighterHooks/ZFighterPower changes add no other player-visible strings.
- 2026-11-20 10:40 Review (PR #1938 head 94890cc, vault coordinator, reviewer away): changes needed; checks green. 1) ZFighterPower.java:728: the guard drain sits inside 'if (live.charging)', so the guard only costs stamina while charging and in practice never drains; move it out of that block (and fix the indentation: the following '} else if' lost its tabs). 2) Guard at zero stamina stays up: ZFighterHooks.guardStamina ignores Feathers.spend's result and raiseGuardStamina is only checked when raising. Drop the guard (ZFighterGuard.set(player,false)) when the spend fails. 3) ZFighterPower.java:1127-1130 and 1264-1267: ki is spent before transform() checks stamina, so an empty stamina row eats the form's ki and nothing happens. Check stamina before taking ki. 4) transform() is also called from ZFighterBattle.java:426, ZFighterGodRitual.java:490, ZFighterGod.java:95, ZFighterLegendary.java:144 and ZFighterBranchForms.java:287 (awakenings, the God rite, breakthroughs): with the check inside transform() those silently fail at low stamina, and a finished rite can end with no form. Put the stamina check only on the player's own form key paths. 5) Only one of the five picked rows is built; the PR names why (ZFighterPower.java owned by #1911). Fine as part 1, but add a Notes line on this task listing the four rows still to build so they aren't lost.
- 2026-11-20 10:14 Notes: rows still to build after part 1 (stamina for Flash Step, guard and forms, now PR #1938): holding back your power; more Limit Break tiers (three more past x3, health burn each second, base or Super only); unlocked potential (late rite with a Verdant elder or the Keeper - put it in Lore.md and quote it to Andy first); fighting styles with a passive each (one style picked once, changeable by a wish, on top of Kingdoms classes). Only stamina is built; the other four rows wait, and Z Fighter power edits must respect that zfighter-aura-rework (#1911) owns ZFighterPower.java until it merges.
- 2026-11-20 10:23 Text pass: reviewed lang/parts/zfighter_stamina.json - message.jaymospowers.zfighter_stamina_empty Running on empty: your stamina is spent. kept, plain and brief; changes/powers/zfighter-gaps-2-power.md kept, player-facing and brief with Built; not played in game. ZFighterHooks, ZFighterPower, ZFighterGuard hunks add no new player strings beyond that key (zfighter_no_ki, zfighter_limit_hurt pre-existing). No code changed, no rewrites needed.
- 2026-11-20 15:38 Review (PR #1938 head 37f8643, vault coordinator, reviewer away): changes needed; checks green, items 2-4 of the last review fixed. 1) ZFighterPower.java:1126-1131 is inverted: 'else if (!ZFighterHooks.enterFormStamina(player)) { ki -= ...; transform(...) }' transforms (and takes ki) only when stamina is EMPTY, and when the stamina spend succeeds nothing happens at all, so stepping up a form with stamina never works. Make it '} else if (ZFighterHooks.enterFormStamina(player)) { ...transform }' as the 1263 path does. 2) ZFighterPower.java:727: the closing brace lost a tab ('\t\t}' then '\t\t} else if'), so the block reads wrong; fix the indentation. 3) Still open from last time: a Notes line on this task listing the four rows still to build (holding back, more Limit Break tiers, unlocked potential, fighting styles).
