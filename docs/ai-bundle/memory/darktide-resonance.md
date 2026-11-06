---
name: darktide-resonance
description: Darktide and Resonance, the 6th/7th powers (PR #25, 0.8.28): Andy's design decisions not obvious from code
metadata:
  type: project
  modified: 2026-10-12T03:56:26.792Z
---
2026-10-11: PR #25 (0.8.28, merged to master 2026-10-11 on Andy's "merge pr") added Darktide (Andy's design) and Resonance (Claude's surprise power). Design table:
/mnt/project-files/readout/new-powers-table.md. Jar: /mnt/project-files/darktide-resonance/.

Andy's rulings:
- Darktide katanas/daggers = Balkon's WeaponMod `katana.*` / `knife.*`. Sora's Dagger was removed entirely in PR #152 (0.10.25) at Andy's ask.
- Darktide users: Balkon knives throw only on shift + right click (plain right click = guard).
- "Crippled" = a humanoid hit in the legs that drops into a crawl (not a slow or stun).
- Darktide STARTS corrupted; 60 s Conduit Power cures until next nightfall, then it returns (Andy will add a later item to prevent the return: hook `nightReturnPrevented`). Night gives ≥3 Flow/s.
- Keys (0.8.34, PR #27, merged 2026-10-12; Tide Arms keeps the held blade's tier, bare hands draw your own best Balkon blade): R = hold charge (overhead weapon / punch up to 5); G always cripples; Z = shadow harvest when corrupted (ascended or not), Veil when ascended and cured; shift+C = weapon swap (Tide Arms), tap C = summon shadows (Drown if corrupted, not ascended). No drowning.
- Ascension unlocks fire/magic reflection and the knife kit (Pounce, Fang Barrage, Riptide Dash); ascended C = Tide Arms.
- Gedan stance stays on X; Andy wants genuine animated kendo kamae.
- Resonance Hush: 5 s, never affects the Web symbiote.

Mobs have no crawl pose: Pose.SWIMMING on a mob T-poses upright. Crippled mobs are laid flat client-side (DarktidePose.rotateCrawl), PR #27 (0.8.34).

Left out of the first build: auto-drawing a second knife into the off hand (only Tide Arms gives twin knives); Drown's water is particles only.
