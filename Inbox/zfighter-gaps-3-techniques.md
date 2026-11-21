---
status: in-review
for: hermes
from: claude-cloud-zfighter-checklist-thread
asked: 2026-11-19
claimed_by:
pr: 1939
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWJ3UmFH2PavpakbTsfUgXo4
writer: hermes-writer
text_pass: done
---

# Z Fighter gaps 3: techniques (ki shell, healing ki, twin discs, ring beam, sword technique, purging beam, sticky bind, rift moves, ki-drinking barrier, ki cell)

**Andy (2026-11-19):** ticked these rows on the Z Fighter gap checklist (https://claude.ai/artifact/UBvYXGNqtkdh8cu2jVCg1L, rows saved in its db doc picks/andy) and said "done, these can go in the vault". His notes on rows are quoted as **Andy's note**.

**Source:** DragonMineZ (GitHub DragonMineZ/dragonminez, GPL-3.0, Forge 1.20.1). Ideas only: our mods are ARR/MIT, so no copied code, art or Dragon Ball names; every feature gets our own name. Check the "Already in" list on the checklist page before building, so nothing is built twice.

## Picked
- **Ki shell** (DMZ Ki Protection): a trained passive that soaks part of every blow from ki when not guarding.
- **Healing ki for anyone**: a heal shape in the technique creator (a ball or touch that heals a friend).
- **Twin discs** (DMZ Double Kienzan): two discs at once, a step up from Ki Disc.
- **Ring beam** (DMZ Kill Driver): a spinning ring of ki that cuts along a line.
- **Sword technique** (DMZ Shining Sword Attack): a blade flurry ending in a ki burst, for fighter weapons.
- **Purging beam** (DMZ Spirit Breaking Cannon / Soul Punisher): hurts the corrupted and the dark far more, breaks possession and living nightmares. **Andy's note:** "patrolmen like this technique" (Galactic Patrolmen use it: ZFighterPatrol/ZFighterNpcKi, and they can teach it).
- **Sticky bind** (DMZ gum bind): a Rubber-folk wad that sticks a foe; mash to tear free (the out-cold mash).
- **Rift fist and rift step** (DMZ Dimensional Fist/Warp): Divine-born; punch into a small rift to hit from behind, step through a rift over a short gap.
- **Ki-drinking barrier** (DMZ Absorption Barrier): the android's G; ki blasts that hit it fill his ki.
- **Ki cell** (DMZ Ki Battery): a Tinkerer craft that gives back a chunk of ki.

**Where to start:** ZFighterArts.java, ZFighterKi.java, ZFighterDisc.java, ZFighterCreator.java, ZFighterSlots.java, ZFighterPatrol.java, ZFighterNpcKi.java, ZFighterAndroid.java, ZFighterDivineMoves.java, ZFighterItems.java.

## Rules
- One PR, branch `claude/zfighter-gaps-3-techniques`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Every Minecraft API checked against 26.3.
- No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better"): reuse player-model fighters, MCA villagers and existing mobs.
- The aura task (zfighter-aura-rework) owns ZFighterPower.java, ZFighterKiAura*.java and ZFighterAuraFlame.java while it is open: keep edits there to one-line hooks, or wait for it to merge. The zfighter-gaps tasks all touch Z Fighter code, so build them one after another (Powers merges one at a time), in number order unless Andy says otherwise.
- No slash commands for player features, no discovery chat text (standing rulings). Abilities.md rows for every new input.
- `python3 tools/pr_checks.py` passes; compile check in the cloud.

## Notes
- 2026-11-20 07:33 Text pass: added missing en_us lang strings in src/main/resources/assets/jaymospowers/lang/parts/zfighter.json — technique names and descriptions for Ki Shell, Twin Discs, Ring Beam, Sword Technique, Purging Beam, Sticky Bind, Rift Fist, Rift Step and Ki-Drinking Barrier, the Ki Cell item name, and the ten new overlay messages (ki cell restored/full, ki barrier android-only/absorbed, rift fist/step blocked, sticky bind stuck/mash/free, sword technique no blade). All keys, placeholders and format codes match the code; no code changed.
- 2026-11-20 12:38 Review (PR #1939 head b3abcd0, vault coordinator, reviewer away): changes needed; checks green, but much of it is never wired in. 1) None of the new classes' init() is called anywhere (git grep for ZFighterStickyBind.init/ZFighterKiCell.init/ZFighterRiftMoves.init etc. finds nothing): the Ki Cell item is never registered, Sticky Bind's server tick never runs (a stuck foe never counts down or comes free), and the other init hooks do nothing. Call each from ZFighterPower.init or ZFighterArts' init. 2) Ki Shell does nothing: ZFighterPower.java:496 ALLOW_DAMAGE computes the reduced amount and throws it away (empty if-block, 'heal the difference after the fact' never happens). Also the task asks that it soak part of EVERY blow, paid from ki, when not guarding; ZFighterKiShell.onHurt only touches ki damage and spends no ki. Hook the real damage modifier (ZFighterHooks.modifyDamage chain) and draw ki for what it soaks. 3) The new attachments (ZFighterStickyBind.STUCK, ZFighterRiftMoves.RIFT_EXIT, ZFighterKiDrinkingBarrier.ABSORBED_KI) aren't listed in JaymosPowers.registerAttachments() (CLAUDE.md rule). 4) Sticky Bind isn't gated to Rubber-folk (task: 'a Rubber-folk wad'), spends ki before looking for a target (ZFighterStickyBind.java:91-99, ki lost on 'no foe'), and its mash (ZFighterPower.java:814) fires every tick sneak is held, so holding sneak frees you in a moment; count presses (edges), and use the existing out-cold mash as the task says. 5) Rift Fist / Rift Step have no Divine-born check (ZFighterRiftMoves.java has none). 6) Purging Beam: Andy's note says Galactic Patrolmen use and teach it; nothing in ZFighterPatrol/ZFighterNpcKi changed. It also spots possession by a description-id substring (ZFighterPurgingBeam.java:73); use the real possession/nightmare checks. 7) Ki Cell: no Tinkerer craft is added (only a creative-tab entry), and no item model/texture. 8) ZFighterKi.throwDisc (ZFighterKi.java ~948) and ZFighterCreator lost their indentation; Bolt made public only for one hook. 9) ZFighterSwordTechnique.init calls ZFighterHands.init() again (double-registers its events if ever called). 10) ZFighterPower.java edits go past one-line hooks (an inline ALLOW_DAMAGE lambda) while #1911 owns that file. Build each row fully or name the cut in the PR.
- 2026-11-20 11:10 Text pass: reviewed all player-visible strings in PR #1939 (lang parts/zfighter.json, change note). The ten technique/item names, descriptions and overlay messages already read plainly; added the two missing overlay strings the code references but the lang lacked: message.jaymospowers.zfighter_rift.divine_only ('Only the Divine-born can open a rift.') and message.jaymospowers.zfighter_sticky_bind.rubber_only ('Only Rubber-folk can throw this wad.') — without them players would see the raw key. No code changed.
