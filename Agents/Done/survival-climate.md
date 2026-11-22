---
status: done
task: survival picks track 2 (Kitchen climate and stamina)
branch: claude/survival-climate
with: Claude (desktop session, started by the survival picks coordinator)
pr: 1995
mods: kitchen, powers
versions: 
started: 2026-11-22
owns:
  - kitchen/src/main/java/com/jaymoskitchen/climate/Thirst.java
  - kitchen/src/main/java/com/jaymoskitchen/climate/BodyHeat.java
  - kitchen/src/main/java/com/jaymoskitchen/climate/Climate.java
  - kitchen/src/main/java/com/jaymoskitchen/climate/Wetness.java
  - kitchen/src/main/java/com/jaymoskitchen/climate/Stamina.java (new)
  - kitchen/src/client/java/com/jaymoskitchen/client/climate/ClimateHud.java
  - kitchen/src/main/java/com/jaymoskitchen/mixin/ThirstSprintMixin.java
  - kitchen/src/main/java/com/jaymoskitchen/mixin/SprintStaminaMixin.java (new)
  - src/main/java/com/jaymospowers/stamina/FireRoll.java (new), one line in stamina/Dodge.java
  - docs/HUD-Layout.md (two rows)
---
# Survival picks, track 2: climate and stamina

**Task:** Andy's LSO / Walk Jog Run picks: thirst per action, a wetness meter and dousing, temperature sources
(height, time of day, heat sources within 20 blocks, 90 s grace), cold breath, sprint stamina capped by hunger, and
"we also stop drop and roll to put fire out". Plan: `docs/Survival-Picks-Plan.md` track 2 (research worktree).

## Plan
- Thirst: per-action costs into Thirst's drain (LSO exhaustion / 4 per point); hot thirst and cold hunger already there.
- Wetness: one Kitchen meter (0-100) replaces BodyHeat's wet timer; Powers' Wet/Soaked feed it; crouch-use water douses.
- Temperature: one LSO degree = 2.5 body heat; height from sea level, a smooth day curve, heat sources out to 20 blocks.
- Breath at the mouth when the air or body is cold. Stamina bar in the focus band. Stop-drop-roll: already in Powers (Crawl rolls).

## Notes
- Builds go through CI / the Deck (Andy's PC runs only the game).

## Done
PR #1995. Thirst costs per jump/block/attack/sprint; wetness meter (HUD glass) is the one wet chill, sneak-use water
to douse; height from sea level, day curve, heat sources to 20 blocks (halved outdoors), 90 s grace; breath puffs at
the mouth; sprint wind bar in the focus band; stop-drop-roll already existed (Crawl), speedsters tireless. Not played: HUD
placement, breath look, heat-scan cost.
- 2026-11-22 14:29 PR #1995 is merged into master; moved to Agents/Done.
