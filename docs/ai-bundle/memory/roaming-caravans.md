---
name: roaming-caravans
description: Pillager Caravans ask remade as Kingdoms RoamingCaravans (hostile pillager + neutral merchant caravans), PR #169 MERGED, Kingdoms 0.7.13 / pack 0.10.55
metadata:
  type: project
  modified: 2026-10-14T16:43:27.729Z
---
2026-10-14 Andy asked to work Pillager Caravans (Modrinth; 26.1 max, Obscuria licence) into our caravan system with hostile/neutral caravans. Built as `RoamingCaravans` in Kingdoms (reuses Caravans helpers, Roads.ahead), PR #169 on branch claude/project-thread-0zdq69, Kingdoms 0.7.13 / pack 0.10.55. MERGED 2026-10-14 16:49Z; not playtested. Andy asked for rarer: small 3 days, merchants 2, major 7 (thread's pick), then 3%/minute once due.
Pillager caravans spawn near players in the wild (minor/major with ravager + strongbox) and head for an illager camp if one is ahead. Merchant caravans are a wandering trader + llamas/mules + a nearby realm's guards as escort. Op command: `/kingdom roaming [pillager|pillager_major|merchant]`.
Related: [[kingdoms-caravans]].
