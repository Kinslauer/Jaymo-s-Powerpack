---
status: open
for: hermes-b
from: coordinator
asked: 2026-11-08
thread: Discord thread 1569108674721357906 (Tiv)
claimed_by: hermes-b
pr:
suggested_owns:
---
# Blindness: echolocation power

**Andy's words (2026-11-08):** Tiv wants both the mimic and blindness tasks sent to the inbox.

**What's wanted:** A blindness power built on Jaymo's Powers:
- Total blindness: pitch-black world, sound-wave echolocation (sonar rings ripple from sounds)
- One-eye blind: vision cuts off on blind side, remaining view stretches to fill screen, aim drifts toward blind side
- Variants: blind side blurs ("Borrado") or darkens ("Escuro")
- Eye damage tracking: BodyPart.EYE not yet in wounds system (only HEAD tracked); global trigger to blind nearby players on head/eye damage

**Implementation plan (from YOUTH's proposal):**
- Server side: new power class in com.jaymospowers.power.*, registered via Powers.register, activated with tick() and onKey()
- Client side: PowerClient drawing full-screen overlay after MISC_OVERLAYS (like CorruptionClient vignette)
- Echolocation rings: server hears nearby sounds, tells client where to draw rings

## Notes
- Debated whether it should be a "random-awakening (fated) power" or obtained via a calling/item.
- No existing worktree or branch for this topic yet.
- 2026-11-08 17:21 hermes-a's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-08 17:24 Andy (2026-11-08): reassigned to Prometheus; hermes-a's run stopped before finishing.
- 2026-11-08 17:26 Andy (2026-11-08): swapped to Bacchus (hermes-b); Prometheus takes the mimic.
- 2026-11-08 17:36 hermes-b's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-08 19:26 hermes-b's last run stopped before finishing; handed back as open so it can carry on.
