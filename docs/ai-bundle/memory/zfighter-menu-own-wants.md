---
name: zfighter-menu-own-wants
description: PR #977 (Kingdoms 0.7.555, merged 2026-11-05): Judge's rebirth menu restored, trimmed Z Fighter MCA screen, villagers' own wants by personality, whale trades narrowed
metadata:
  type: project
---
2026-11-05. Not played yet.
- Bug: #952 gave Z Fighter "Have a word" the id `jaymoskingdoms_word`, same as villagers' Talks "Have a word"; Judge's own menu (be born again) became unreachable. Never reuse an MCA answer id.
- Andy's ruling: Judge/Keeper/masters get a TRIMMED screen, not full and not talk-only: page Talk + Trade (McaZFighterPageMixin); talk menu Talk about work (their menu), Talk about training, Chat; pilots/patrolmen also joke/story (ZFighterFolk.keeps). No hugs/rumours/flirting.
- Andy: not everyone trades for whale goods. WhaleTrade only for trades that use them (old blanket offers stripped). OwnWants: every villager one own want, mostly by MCA personality (byNature), else trade, else small pleasures; stable per UUID.
- #989 (Powers 0.20.78, merged 2026-11-05): Judge's "be born again" never greyed; living visitors reborn on the spot (Andy: always able to reset at the Judge).
Related: [[whaling]], [[mca-in-pack]].
