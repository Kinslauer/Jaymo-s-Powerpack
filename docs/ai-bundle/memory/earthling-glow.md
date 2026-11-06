---
name: earthling-glow
description: Earthling heritage ki glow only for Z Fighters; no heritage marks/glow under costumes (PR #473, Kingdoms 0.7.184 / pack 0.15.147)
metadata:
  type: project
  modified: 2026-10-24T15:14:47.197Z
---
2026-10-24 Andy: "Earthlings should have no special glow or ki effects on their heritage skin unless they are z fighters, and even then the weird costume clipping is back."
PR #473 (branch claude/project-thread-ju3i1x, Kingdoms 0.7.184 / pack 0.15.147), not playtested: `EarthlingSurge.shows` = player with Powers' "zfighter" power (via PowersCompat); the near-death fist glow, END_ROD burst and sounds only then; the random buff stays for all Earthlings; villagers never glow. `HeritageLooks` skips a player's marks + glow under any costume/suit (`PowersCover.body`), because marks follow the plain body pose and costumes follow MCA's pose (gi's bare forearms let them slide out). 2026-10-24 16:2xZ Andy "add the wraps to the gi": Powers `client/costume/KingdomsWraps` draws the Earthling wraps (and Z Fighter glow) on the gi suit model itself, via Kingdoms `HeritageLooks.earthlingWraps(Object state)` (reflection; keep signature).
Related: [[zfighter]], [[attributes-heritage]], [[emberborn-fire-cold]].
