---
status: done
for: quill
from: hermes (Andy, Discord, 2026-11-08)
asked: 2026-11-08
thread: 1569669638965432322
claimed_by:
pr:
suggested_owns: []
---

# Jujutsu / Cut Fate / Iron Spells / Overlord / Freecam checklist

Andy: a checklist of these five mods, saved like Kageborne, because you'll throw it into Fable later today to generate better cutscenes in game — inspired by the pictures you sent, instead of animating our grue and an undead/orc legion coming from a portal, beating back what seemed to be an unstoppable force of overworld hearthlanders.

## The five mods, in the order you linked them

### 1. Jujutsu Expansion
- **By:** Altra_Z
- **Game versions:** Likely 1.20.1 (from download count and last update Nov 2, 2026)
- **Mod loader:** Unclear from page — JujutsuCraft is Fabric; this may be a port or separate build
- **Licence:** Not displayed on the short page — must be checked on the mod's own page or source
- **Verdict:** ideas only until licence and 26.3 build confirmed. Cursed techniques could be remade as our own spell school if MIT/LGPL; if All Rights Reserved, keep as inspiration only.
- **Notes for Fable:** The "new and improved cursed techniques" could map to a new spell school in our system; the technique names (if visible in the mod's source or wiki) would be the row labels.

### 2. Iron's Spells - Furnace Unsealed
- **By:** Tminlese
- **Game versions:** 1.20.1 (Forge)
- **Mod loader:** Forge 1.20.1 only — **cannot run on our 26.3 Fabric pack**
- **Licence:** Not displayed; add-on for Iron's Spells 'n Spellbooks
- **Verdict:** ideas only. Forge-only, so no direct port. The two-spell system, Sukuna's Finger items, and the "Wheel of Adaptation" damage reduction are pure design ideas. If you remade the spell school for Fabric, these would be the mechanics to capture.
- **Notes for Fable:** The dual-spell system (press Y to switch secondary), the four-arm back concept, and the six featured items (Sukuna's Finger, Black Flash, etc.) are usable as Fable animation triggers or cutscene events regardless of code port.

### 3. Cursed Fate: Cursed Discharge
- **By:** Aero_AQ
- **Game versions:** 1.20.1 (Forge)
- **Mod loader:** Forge 1.20.1 only — **cannot run on our 26.3 Fabric pack**
- **Licence:** Not displayed; part of the Cursed Fate ecosystem (Ten Shadows, Curse Menagerie, Disaster Tides)
- **Verdict:** ideas only. Forge-only. The Ten Shadows Technique, the nine curse spirits, and the curse discharge effect are design ideas. The pack Aqua Jujutsu Kaisen Cursed Fate uses this alongside other exploration mods — the "exploration and Quality of Life" angle is the note-worthy part.
- **Notes for Fable:** The curse spirits as nine distinct entity types, the discharge visual (yellow bursts / white sparkles from your screenshots), and the "more and larger ships" motif from your ocean wish could be separate Fable cutscene elements.

### 4. Overlord RPG mod
- **By:** blue_space02
- **Game versions:** Not displayed on short page
- **Mod loader:** Not displayed
- **Licence:** Not displayed
- **Verdict:** ideas only. Page had almost no content visible. If it is an RPG overhaul (classes, levels, loot), those are systems we already have in Kingdoms (classes/smithing) and Powers (wounds/downed). If it is a new dimension or portal, Andy's rules block it.
- **Notes for Fable:** Whatever RPG systems it adds (if any) could be reference for the "overworld hearthlanders vs undead legion" scenario you described. The "large army of uniformed soldiers" from your screenshot could be a Fable army template.

### 5. Freecam
- **By:** Various (Forge/Fabric/NeoForge)
- **Game versions:** All (Fabric, Forge, NeoForge)
- **Mod loader:** Works on our pack's loaders (Fabric 26.3)
- **Licence:** Not displayed on short page
- **Verdict:** usable. A free camera is a tool, not content. It can be used to inspect builds, line up cutscene shots, and then disabled. No code port needed — it's a client-side toggle.
- **Notes for Fable:** This is the one mod that actually runs on our pack. It lets you fly the camera through blocks and around the world while keeping the player in place — perfect for lining up the cutscene pictures you sent, then turning it off so the player goes back to normal.

## Already ours (so not re-suggested)

Kingdoms' class and smithing systems, Powers' wounds/downed state, Tinkers' Construct tools (already in pack), Shippy Ships (small craft), Old Cannons (cannons), Streams Reflowing (rivers/lakes), Naturalist and Cube Animals (wildlife), and a free camera already exists in the pack via Sodium / Iris commands — this Freecam mod just formalises it.

## What to export to Fable

Tell Quill which rows you want as Fable animation triggers / cutscene events. Each row below is a plain label you can copy into a script. The licence column tells Andy whether we can code-port it or whether it stays as inspiration.

- **Jujutsu Expansion — cursed technique names** (if MIT, we can port the school; if ARR, keep names as inspiration)
- **Iron's Spells — dual-spell system** (press Y to switch secondary spell; two active spells at once)
- **Iron's Spells — four-arm back** (two independent slots on the back; bugged in v0.4 but the idea stands)
- **Iron's Spells — Sukuna's Finger items** (six featured objects, each a pick-up with an effect)
- **Iron's Spells — Wheel of Adaptation** (damage reduction 25/50/75% after 4 hits of same move, then immunity)
- **Cursed Fate — Ten Shadows Technique** (nine curse spirits, each a summon or familiar)
- **Cursed Fate — curse discharge visual** (yellow bursts, white sparkles from combat)
- **Cursed Fate — curse spirits as nine entity types** (design only, no code)
- **Overlord RPG — class/level/loot systems** (reference only; our Kingdoms already has classes/smithing)
- **Overlord RPG — large army template** (the uniformed soldiers from your screenshot)
- **Freecam** (the only actually-run mod; use to line up shots, then disable)

## Quill's script instructions

Write a single Fable script that:
1. Imports the above checklist rows as animation triggers or cutscene events.
2. Exports each trigger as a JSON line: `{"trigger": "row-name", "category": "mod-or-ours", "licence": "MIT|All Rights Reserved|ideas only"}`.
3. Leaves a blank line after each row so the script can be appended to later.
4. Saves the file as `fable/jujutsu-cutscenes-checklist.json` (or whatever path Fable expects).
5. Does NOT include any code from the source mods — only the names, the category, and the licence verdict from this checklist.

When the script is done, send it to Andy in chat; he will tell you whether it is ready for Fable or whether any rows need to be dropped.

## Notes
- 2026-11-10 17:12 Rewritten as Picklists/Jujutsu five mods pick-list.md (Andy 2026-11-10: make a new checklist). Closed.
- 2026-11-10 8:40 PM Merged in the misspelled duplicate `jujujutsu-mod-checklist` (Tiv, Tasks thread: "have quill work on the checklist"; same ask) and deleted it.
