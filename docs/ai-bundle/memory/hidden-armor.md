---
name: hidden-armor
description: Hide-armour eye buttons beside inventory armour slots (core/HiddenArmor), merged PR #100 as pack 0.8.117
metadata:
  type: project
---
2026-10-13: Andy asked for a cosmetic armour panel or per-piece hiding. Built per-slot hide toggles (merged PR #100 as pack 0.8.117).
Hidden pieces still protect; synced to all clients, saved, kept through death; render state cleared last, so Kingdoms ArmoryLayer and symbiote engulf work. Elytra stays visible.
Existing 26.3 Fabric hide-armour mods (Hide Your Armor, Zannagh's Armor Hider) are client-only, so they were rejected.
Cosmetic overlay slots (wear a different look) were NOT built; offered as a follow-up.
**Why:** others must see the look. **How to apply:** extend core/HiddenArmor for any cosmetic-slot follow-up.
