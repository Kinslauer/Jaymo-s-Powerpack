---
status: open
for: apollo
from: Andy (Claude chat, 2026-11-23)
asked: 2026-11-23
claimed_by: hermes-a
pr: 
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
