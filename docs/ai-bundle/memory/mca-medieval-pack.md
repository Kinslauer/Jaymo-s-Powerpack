---
name: mca-medieval-pack
description: "MCA - Default Medieval" resource pack (CurseForge 667175) merged as pack 0.8.58 (PR #47), auto-enabled once
metadata:
  type: project
  modified: 2026-10-12T07:31:34.061Z
---

2026-10-12: Andy asked for the MCA - Default Medieval resource pack (by DE4THR4SH). Merged as Powers/pack 0.8.58 (PR #47).
It's in `packResourcePacks` in build.gradle (CurseForge manifest entry 667175/8868890; all rights reserved, so never
shipped in overrides), profileMods downloads it from forgecdn, and `client/MedievalVillagers` (renamed PackResourcePacks in PR #154) enables it once per install
(marker `config/jaymospowers-medieval-villagers-v1`), adding it to `incompatibleResourcePacks` too because its file is
pack_format 55 (1.21.1) and 26.3 `Options.loadSelectedResourcePacks` would otherwise drop it.

**Why:** 1144 of our MCA jar's 1145 skin paths match, so it works with the self-built MCA ([[mca-in-pack]]).

2026-10-13: Andy reported villagers still in MCA's own clothes; the enabler only worked if the zip was already in
resourcepacks/. PR #91 (merged 2026-10-13, pack 0.8.106) makes `MedievalVillagers` download it from forgecdn (SHA-1 63400dbf...) when missing,
marker bumped to v2. Textures override by path, so no villager re-dressing is needed.

**How to apply:** more resource packs go in `packResourcePacks`; a newer pack file needs its id/name updated there and
the marker bumped (v3 next) plus FILE/URL/SHA1 in MedievalVillagers. Not on Modrinth; CurseForge site 403s from the cloud, api.cfwidget.com works for file ids.
