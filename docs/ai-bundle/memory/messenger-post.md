---
name: messenger-post
description: Letters, messenger pigeons, MCA couriers riding across the land, and world news/MCA mail by letter instead of chat (PR #507, Kingdoms 0.7.201 / pack 0.16.14)
metadata:
  type: project
  modified: 2026-10-25T03:56:34.516Z
---
Andy 2026-10-25: "i want a messenger system for getting information across the lands ... less information through chat" (after Courier Owls, ARR, remade not copied).
PR #507 MERGED 2026-10-25 04:08Z (Kingdoms 0.7.201 / pack 0.16.14), not playtested yet. Classes: `Letters` (blank/sealed letter + `CONTENTS` component, write/read screen), `Pigeon` (parrot base, no shoulder/mimicry), `PigeonPost` (birds out of world in a record, come down near the recipient), `Couriers` (rewritten: saved errands, ride past 160 blocks, off-world along King's Roads like `Elders`), `Post` (character-name directory, news queue, MCA mail pulled from inbox, `later` queue).
`KingdomEvents.announce` no longer broadcasts to chat: it goes to `Post.news` → "Tidings" letters. MCA's "You have mail" toast is cancelled (`McaMailMixin`).
**How to apply:** new world-wide announcements should call `KingdomEvents.announce` / `Post.news`, not broadcastSystemMessage. See [[kingdoms-caravans]], [[mca-in-pack]].
