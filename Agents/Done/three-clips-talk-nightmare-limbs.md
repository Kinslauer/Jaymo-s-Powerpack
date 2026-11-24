---
status: done
task: Andy's three clips (interpersonal convo, invulnerable nightmare, bigger priorities)
branch: claude/small-talk-back, claude/nightmare-shot-tell, claude/wounded-want-limbs
with: Claude Code (side session)
pr: 2046, 2048, 2047
mods: kingdoms
versions: 
started: 2026-11-23
owns:
  - kingdoms Neighbours.java, Overheard.java, Routines.java (idle line)
  - kingdoms NightmareTell.java, mixin/graveyard/
  - kingdoms FrostTech.java (limb helpers), IllagerTalk.java (askWant, give)
---
# Three clips: small talk, the Nightmare, the pillager's limbs

**Task:** Andy's clips: NPCs no longer make small talk with each other; is the Nightmare meant to be invulnerable; a pillager missing two limbs wants gold.

## Done
- **Small talk (PR #2046):** #1981's boredom made villagers take up pastimes within seconds; Neighbours' meetings waited for two with no pastime and Overheard scenes lost their pair to the pastime's walk target. Meetings and scenes now take villagers out of pastimes. Meetings still keep to the day (by design since Oct 14; the clip was at night).
- **Nightmare (PR #2048):** The Graveyard's Nightmare ignores all projectile damage (WeaponMod's flintlock ball is tagged is_projectile). By design; now a shot puffs smoke through it and the shooter is told only steel up close will bite. Lore.md (new) paragraph added.
- **Limbs first (PR #2047):** illagers missing limbs ask for a wooden leg/arm (and say how many) before their nature's want; any Powers stand-in limb handed over is fitted (not drawn on illagers).
- All built in CI only; none played.

## Notes
- 2026-11-23 16:24 PR #2046 is merged into master; moved to Agents/Done.
- **Purse icon and money bags (PR #2063, branch claude/purse-icon-money-bags):** Andy's picks from Numismatic Overhaul. With a coin purse worn, a purse in the inventory lists every coin you carry by currency; chests now and then hold a filled coin purse. CI green, not played.
- **Banks:** started on `claude/realm-banks` (WIP, never built) and handed to the coordinator: `Inbox/realm-banks-finish.md`.
