---
name: web-shooters
description: Web shooters + cartridges (optional upgrade), braided web lines, true-rope swing, PS5 symbiote slams/surge/tear-off, tendrils on ridden mobs; PR #292 MERGED (pack 0.12.4)
metadata:
  type: project
---
2026-10-16 Andy sent JADEDD's "I Coded Spider-Man Into Minecraft" (YouTube can't be fetched from cloud; he uploaded /mnt/project-files/swinging.mp4 + spidey.mp4; stills in /mnt/project-files/web-shooters/refs/). He likes its web design and web shooter idea; physics as a true rope; webs must NOT stick to air.
Rulings: shooters are an OPTIONAL upgrade (decision card); symbiote counters/abilities animate like PS5 Spider-Man 2 tendril slams + surge, suit removal like Spider-Man 3 tear-off; mobs ridden by the symbiote get Living Armor tendrils that help them attack.
PR #292 MERGED 2026-10-16 as pack 0.12.4 (#295 took 0.12.3; branch claude/project-thread-vb1xbx), jar /mnt/project-files/web-shooters/. Not playtested.
**How to apply:** WebShooters.REQUIRED flips shooters to required. Braid in WebLineRenderer.braid; rope in WebSwing (ROPE_PASSES/ROPE_MAX_CATCH). Symbiote: WebHaul, WebTendrilBundle, WebSuitTear, WebBehemoth.SLAM. Thrash/hook-claw blood still exists (Andy wants none). Another project also works on the symbiote: bring master in before merging.
