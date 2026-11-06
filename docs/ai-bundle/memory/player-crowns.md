---
name: player-crowns
description: Players can wear a realm's crown (Crowns.java, PR #542, Kingdoms 0.7.225 / pack 0.17.10): usurp, inherit by marriage, conquest claim, /kingdom crown found
metadata:
  type: project
  modified: 2026-10-26T17:33:32.375Z
---
2026-10-26 Andy asked "How does a player become king?" (answer: no way existed; Lord rank was the top), then "Build all your ideas in please".
PR #542 (MERGED 2026-10-26 18:48Z, Kingdoms 0.7.225 / pack 0.17.10, compiled only): `Crowns` attachment maps faction id to player sovereign; the villager leader stays on the throne as steward (steward death = new steward next day, no fate). Routes: Lord kills ruler within 16 of throne (usurp); MCA spouse inherits; Lord who takes a war seat / slays ruler gets a 1-day `/kingdom crown claim <name>`; `/kingdom crown found [name]` 256 emeralds, 320 blocks from realms. `/kingdom crown`, `abdicate`; sovereign is Lord rank, no crimes in own realm, one crown each.
Related: [[vassal-lords]], [[great-wars-creeds]].
