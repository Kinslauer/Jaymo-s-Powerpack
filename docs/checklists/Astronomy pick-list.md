---
type: picklist
source: Astronomy (Jubileu_13)
url: https://www.curseforge.com/minecraft/mc-mods/astronomy/gallery
licence: MIT · NeoForge 1.21.1 · verdict: remake our own
status: picked
created: 2026-11-08
tags: [inbox, picklist, astronomy]
---

# Astronomy pick-list

A young beta mod (0.3.0, one month old, NeoForge 1.21.1) that turns the night sky into something to study: a real star catalog, constellations that appear as you find them, a telescope, an armillary sphere, and a journal of measurements with its own skill ladder. MIT, so the code and data are usable with the notice kept — but there is no 26.3 Fabric build, so this is a remake, not an install.

Andy's framing: "add this but in the high renaissance artstyle" — the style reference is `Assets/astronomy-reference.png` in the vault.

Tick what you want, add a note under any row, then set `status: picked`.

## The sky

- [x] **A real star catalog over our night sky** `L` ^as-catalog
  The mod ships 8,920 stars from the HYG 4.1 catalog, down to magnitude 6.5, and draws its own sky instead of vanilla's.
  For us: a replacement sky renderer is the expensive part — it has to sit beside our shaders and Sodium without fighting them. The catalog data itself is the cheap half.
  Notes:

- [x] **Constellations that connect as you find them** `L` ^as-figures
  88 figures, 695 line segments; the figure only appears once you have discovered the stars in it.
  For us: this is the good idea — the sky rewards looking, rather than being wallpaper.
  Notes:

- [x] **Sky that depends on where and when you stand** `M` ^as-place
  Position and world time change what is above the horizon; light pollution dims it near settlements.
  For us: light pollution is a fine reason for a village to feel different at night from a hilltop.
  Notes:

## Instruments

- [x] **The telescope** `M` ^as-telescope
  A basic telescope to start observing; an Altazimuth Mount skill adds azimuth and elevation scales, with the aim saved per telescope.
  For us: a placeable, aimable block — closer to our cannons and ballistae than to most furniture.
  Notes:

- [x] **The armillary sphere** `L` ^as-armillary
  An instrument that measures anything you have already discovered: position, location, world time, with animated mechanical feedback.
  For us: the best-looking object in the mod and a natural centrepiece for an observatory room.
  Notes:

- [x] **Measurements that can fail** `M` ^as-weather
  A reading depends on daylight, weather, a clear sky and the target being above the horizon.
  For us: our weather and seasons already exist — this gives them a use indoors and at night.
  Notes:

## The journal

- [x] **A journal of measurements with a skill ladder** `L` ^as-journal
  Earn points for fundamental astronomy, spend them on skills, grow Astrometry through 20 levels; only unique measurements count.
  For us: overlaps Kingdoms' scholar side and Powers' levels — decide which one owns it before it exists twice.
  Notes: Should be worked into the scholar side.

- [x] **Study pages with real formulas** `M` ^as-study
  Pages explain the ideas with units, formulas and examples, and say plainly where the simulation stops being true.
  For us: this is the honest version of a lore book — it teaches the mechanic instead of narrating a story.
  Notes:

## Sun and moon

- [x] **Eight lunar phases and a Sun you can observe** `M` ^as-phases
  A simple orbital model drives both; square or round body is a config choice.
  For us: our nights already have weather and monsters; phases give moonlit nights a rhythm.
  Notes:

- [x] **Looking at the Sun without Optics blinds you** `S` ^as-blindness
  A valid click on the Sun before the Optics skill applies a simulated blindness.
  For us: we already have a blindness power and a one-eye overlay — reuse that feel, not a new effect.
  Notes:

## Art direction — high renaissance

The reference is a da Vinci notebook page: sepia ink on aged paper, architectural line work, cross-hatching that follows the form, faint diagrams and handwriting filling the background, everything drawn as a study rather than a picture. Four rules that come out of it:

- **Ink, not paint.** Brown-black line on warm paper white. No saturated colour anywhere except where a material demands it (brass, glass).
- **Hatch for volume.** Shadows are layered parallel lines that follow the curve of the object, never gradients or soft shadows.
- **Draw the construction.** Faint circles, centre points and guide lines left visible — the page should look worked on.
- **Instruments look studied, not sleek.** Wooden tubes, brass rings, tripod legs with joints; nothing glossy or sci-fi.

What that means in practice: a 16×16 item icon can only *suggest* this — brown ink lines and a hint of hatching, no gradients. The style really lives in the journal pages and the armillary sphere's model, where there is room for the paper, the diagrams and the hand.

## Notes



## Already ours

Weather, seasons and cold (Kitchen climate, WinterCold and the weather system), blindness as a power, levels and skill ladders in both Kingdoms and Powers, and a night sky that is otherwise vanilla. Nothing astronomical exists in the pack today.
