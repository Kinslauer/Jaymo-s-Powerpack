---
type: picklist
source: Clay Soldiers + Clay Warfare + Symbiotes Unleashed + Symbiote Extra Visuals + Hand of God
url: https://modrinth.com/mod/clay-soldiers · https://modrinth.com/datapack/clay-warfare · https://www.curseforge.com/minecraft/mc-mods/symbiotes-unleashed-extra-visuals · https://www.curseforge.com/minecraft/mc-mods/symbiotes-unleashed · https://www.curseforge.com/minecraft/mc-mods/hand-of-god
licence: Clay Soldiers MIT · Clay Warfare All Rights Reserved (community reuse with credit) · Symbiotes Unleashed All Rights Reserved · Symbiote Extra Visuals All Rights Reserved · Hand of God unknown
status: picked
created: 2026-11-21
tags: [inbox, picklist, small-armies]
picked: 2026-11-26
---

**Pick on the page, not here:** https://claude.ai/artifact/16LbvMbbHXJuH2qUMngRP4 (with the other questions waiting on Andy; ticks and notes save to db picks/andy). This file is the source draft.

# Small-armies and divine powers pick-list

Five links, one verdict up front: **none of them run on our pack as-is.** Clay Soldiers is MIT but its newest Fabric build is 1.20.6; Clay Warfare is a datapack for 26.3 but All Rights Reserved; Symbiotes Unleashed, Extra Visuals and Hand of God are Forge 1.20.1. Every row below is an idea we'd rebuild on our own classes, not a port.

Tick what you want, add a note under any row, then set `status: picked`.

## Clay soldiers

- [ ] **Tiny fight-for-you soldiers** `M` ^cs-soldiers
  Clay soldiers you craft from clay and soul sand, right-click to place, opposing colors fight each other. Upgrades: stick, flint, glowstone, leather, wool armor, dye colors.
  For us: our own mob family — small, short-lived, player-placed fighters. Plugs into Powers' existing mob AI and the Kingdoms haven guard.
  Notes:

- [ ] **Clay remote / mass clear** `S` ^cs-remote
  An item that removes all clay soldiers within 30 blocks.
  For us: a player command or item on our soldier/guard system.
  Notes:

## Clay Warfare datapack (runs on 26.3, All Rights Reserved)

- [ ] **Five colored soldier types** `S` ^cw-colors
  Grey, blue, red, yellow, white soldiers with different base stats.
  For us: color variants of our clay soldier, no stat differences needed.
  Notes:

- [ ] **Buffs and equipment** `M` ^cw-buffs
  Throw items at soldiers: sugar = speed, chicken = more HP, porkchop = heal, lapis = damage, slime ball = pin to ground, gunpowder = explode on death, amethyst = juggernaut (bigger, more HP, slower).
  For us: throwable buff items on our soldier system; juggernaut = a size variant.
  Notes:

- [ ] **Clay remote** `S` ^cw-remote
  Craftable remote to clear all soldiers in radius.
  For us: same as the Clay Soldiers remote row — pick one implementation.
  Notes:

- [ ] **Guide book** `S` ^cw-book
  Players spawn with a book explaining everything.
  For us: our existing advancement/hint system, not a new book.
  Notes:

## Symbiotes Unleashed / Extra Visuals

- [x] **Living tentacle animations** `M` ^su-tentacles
  Animated tentacles, organic armor movement, dynamic symbiote visuals on a host entity.
  For us: GeckoLib animation layer on an existing mob — tentacle bones on our xeno or parasite mobs, not a new entity.
  Notes:

- [x] **Symbiote host possession** `L` ^su-host
  A symbiote bonds to a player or mob, changes their look and abilities.
  For us: Powers already has xenoforms and ultimates — this is a skin and a passive on top, not a new system.
  Notes:

## Hand of God

- [x] **Nine divine powers** `L` ^hog-powers
  Control Sun and Moon, time, matter, reality and lightning — god-like player abilities with visual effects.
  For us: Powers already has time (Chronomancy), weather, lightning, matter (alchemy). This is a power school, not new mechanics.
  Notes:

- [x] **Sun and Moon control** `M` ^hog-celestial
  Move or freeze the Sun and Moon.
  For us: Kingdoms' world time or Powers' existing celestial hooks.
  Notes:

## Notes

## Already ours

Town golems and haven guards (Kingdoms) for the soldier slot, Powers' xenoforms and ultimates for symbiote-like hosts, Chronomancy and alchemy for the divine powers, GeckoLib animation layers already in use, and the advancement/hint system that makes a guide book redundant.
