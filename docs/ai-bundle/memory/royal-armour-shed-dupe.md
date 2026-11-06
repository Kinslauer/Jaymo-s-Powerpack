---
name: royal-armour-shed-dupe
description: King's armour "duplicating" (2026-11-05): Carry.shed on a downed ruler + Rulers.step re-fit made a new plate set per lift; PR #984 MERGED 2026-11-05 07:17Z (Kingdoms 0.7.561, pack 0.20.72)
metadata:
  type: project
---
2026-11-05 Andy: "the king's armor seems to be duplicating when they carry him ... i found a ton of armor".
Cause: Powers Carry.shed drops a downed body's heaviest armour so a player can lift it; once a ruler's regalia helmet went, Rulers.step (no crown) ran fit() -> RoyalDress.reset + Regalia.dress, and RoyalDress.generate later made a fresh trimmed plate set. One new set per lift/recover.
PR #984 MERGED 07:17Z as Kingdoms 0.7.561 / pack 0.20.72 (branch claude/project-thread-ex2ieu, built, not played): missing crown only -> Regalia.recrown (same crown, nothing else); no re-dress or outfit change while downed. Jar /mnt/project-files/royal-armour-dupe/..
**How to apply:** anything that regenerates gear when a piece is "missing" is a dupe if that piece can leave the body as an item. See [[royal-remains]], [[armour-wear]].
