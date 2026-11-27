---
status: claimed
for: apollo
from: Andy (Claude chat, 2026-11-23)
asked: 2026-11-23
claimed_by: hermes-a
pr: 2222
writer: hermes-writer
text_pass: 
claim_handbacks: 1
---

# Court wizards ward their kingdom against creepers

## What's wanted

Andy: "court wizards should be able to cast a spell around their kingdom's plot to prevent or rewind any creeper
explosions, and weaken them within the field, some feel the disturbance and are repelled by the magic, not entering in
the first place."

A kingdom's court wizard casts (and keeps up) a ward over the kingdom's plot. Inside it:
1. **Creeper blasts are stopped or undone**: a creeper going off inside the ward either fizzles before it blows, or its
   blast is rewound (the broken blocks put back, the way they were, with a visible rewind effect). Pick per case
   (e.g. prevented while the wizard is fresh, rewound if it went off anyway) and say which.
2. **Creepers are weakened inside**: slower, a smaller or weaker blast if one gets through (propose the numbers).
3. **Some are repelled**: a share of creepers feel the disturbance at the ward's edge and turn away, never entering.
   Pick the share (or make it depend on the wizard's strength) and keep it readable: a shimmer or a recoil at the edge.

## Notes

- Find the court wizard (Kingdoms; grep `docs/codemap/kingdoms.md` for court, wizard, mage, realm plot, claim) and the
  kingdom's plot bounds. Say how the ward is kept up (always on while the wizard lives and is home? a cost? a cooldown?)
  and what breaks it (wizard killed, away, siege): propose and ask Andy.
- Rewinding a blast: record the blocks an explosion inside the ward would destroy (ExplosionEvent / the detonate hook
  in 26.3; check the sources) and restore them over a few seconds. Never restore into a block a player has since placed.
- Effects through Kingdoms `Fx` (sigil/ring/wave), never recoloured redstone dust. Keep it cheap on the Deck server:
  creepers checked only near warded plots, not every creeper every tick.
- 2026-11-25 23:01 2026-11-25 approved cleanup: Approved cleanup: #1842 blast mending is not this complete ward. Before code, ask Andy to choose upkeep/break conditions and weakening/repulsion tuning; preserve prevention or safe rewind requirement.
- 2026-11-25 23:22 YOUTH delegated this to apollo (Claude approved): ward touches plots, explosions and Fx across classes; propose what breaks the ward and ask Andy in the PR
- 2026-11-26 01:56 Question: Need clarification on ward upkeep, break conditions, prevention vs rewind, weakening numbers, repulsion chance/visual, ward radius.
- 2026-11-26 09:35 Cloud Claude 2026-11-26: Andy's questions for this task are on the tick page https://claude.ai/artifact/16LbvMbbHXJuH2qUMngRP4 (db picks/andy). Wait for his answers there; don't build before he answers.
- 2026-11-26 09:58 Andy's picks 2026-11-26 09:56 on the Waiting on Andy page (https://claude.ai/artifact/16LbvMbbHXJuH2qUMngRP4, db picks/andy): upkeep: always on while the court wizard lives and is home, no cost (suggested, kept). Breaks: the wizard's death, absence from the plot, OR any siege. Stop vs undo: fizzle while the wizard is fresh, rewind any that go off; Andy's note: 'stronger wizards prevent more blasts, but they all rewind within the ward. players have to learn it too' (so prevention chance scales with wizard strength, every blast that gets through is rewound, and players can learn the ward spell). Weakened inside: 30% slower, blast half size (suggested, kept). Repelled at the edge: about half (Andy's earlier Kanban-cleanup answer, Decisions 2026-11-26; the page default was left untouched). Reach: the whole kingdom plot (suggested, kept). Ready to build.
- 2026-11-26 10:27 Text pass: PR #2222 court-wizard-creeper-ward. Player-visible strings: changes/kingdoms/court-wizard-creeper-ward.md (ward behavior, plain and accurate); kingdoms lang parts form_skills.json, message.json, skill-perk.json (couched lance: dropped the not-on-camels-elephants-or-dragons mount restrictions now that dragons are removed); powers lang armory.json (lance charge tooltip shortened). New classes CreeperWard and CourtWizards add no player-facing strings. Validated JSON syntax of all four edited lang parts. No rewrites needed; no code changes.
- 2026-11-27 02:58 Review (head dac86bc), cloud coordinator for Andy: changes needed. 1) ServerExplosionMixin.java:38: 'prevented' only clears the block list in interactWithBlocks, so the creeper still explodes and hurts everyone nearby; a fizzle must cancel the blast itself (cancel at Creeper.explodeCreeper or ServerExplosion.explode HEAD, and discard the creeper with the fizzle Fx). 2) Same line: the rewind path also clears the block list, so nothing breaks and processRewinds then skips every block (not replaceable); there is never a visible rewind. Let the blocks break, record them, and restore them over REWIND_TICKS. 3) Blast half size inside the ward (Andy's pick) is not built: only slowness. 4) CreeperWard.java:445: slowness lasts 10 s and is never renewed while WEAKENED holds the creeper, so creepers get their speed back after 10 s inside. 5) CreeperWard.java:275 and :329: creepers are only searched in a fixed box of +-1024 round 0,0, so kingdoms further out get no weakening or repulsion; and :99 runs that whole-box search every tick. The task said creepers checked only near warded plots: search a box round each warded plot, every 10-20 ticks. 6) CreeperWard.java:288: every creeper near or inside the ward rolls 50% every second, so in the end all of them get shoved; roll once per creeper at the edge and remember it. 7) CreeperWard.java:244 siege removes the ward, but CourtWizards.java:156 puts it back 10 ticks later because the wizard is still home; keep it broken while the siege runs. 8) Andy: 'players have to learn it too': the ward isn't a player spell yet (scroll or skill). 9) CreeperWard.java:230 draws a ring at every claimed chunk corner at sea level every 2 s: show it at the plot's edge, at ground height, or near players. 10) Andy said stronger wizards prevent more; the code makes younger wizards stronger (freshness by age). Use the wizard's strength or level if there is one. CI is green.
- 2026-11-27 00:17 hermes-a's last run stopped before finishing; handed back as changes.
