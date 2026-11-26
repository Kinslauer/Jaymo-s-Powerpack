# Reason look: plan

Andy, 2026-11-26 (Reason Art project): "scan the pack for other script generated art? I'd like to refine everything
into a cohesive artstyle, maybe make a resourcepack for use alongside the mod in place of excalibur". He picked the
look (clean vanilla-plus) and all 25 rows of the Pack Art Scan pick list (https://claude.ai/artifact/FuL1HrVjz295anaVrYdcNV);
only TaCZ is left alone (no-derivatives licence). Scan data: project files `art-overhaul/pack-art-scan/` (flagged.csv).
Rules: docs/Vanilla-Style.md. Spore & Stem's mushrooms are another thread's.

Counts are textures that fail the art check (`tools/art_audit.py`) today.

| Patch | Picks | What | Count |
|---|---|---|---|
| 1 | d_pack, d_excal, d_guide, d_gate | Reason look pack on by default, Excalibur retired, style guide, art check on PRs | done in this PR |
| 2 | k_her_icons, k_her_worn | Heraldry armour: each of the 72 pieces painted once with its own outline, the 15 finishes as ramp swaps plus decals; worn sheets the same way (`kingdoms/tools/gen_heraldry_armour.py`) | 1,066 icons, 918 sheets |
| 3 | k_gear, k_crests, p_eggs | Other Kingdoms gear icons (helms, hats, crowns, frontier/kintsugi/heavenly/despot sets, cloaks, guns, seals, runes), the 19 crests, and spawn eggs in both mods as vanilla's two-colour spotted egg | 286 + 19 + 110 |
| 4 | k_old_dead, k_horses, k_folk | Old dead skins, horse coats (vanilla horse shading, markings as overlays), dyed folk outfits (one base per role, tinted) | 268 + 101 + ~140 |
| 5 | k_mobs | Kingdoms mobs: legends, creatures, dragon hides, Mo' Creatures beasts, draugr, champions, war elephants (Workshop skins keep their CC BY credit) | ~170 |
| 6 | p_fish, p_dusts, p_misc, p_mech, p_models | Powers: 23 fish and their cooked versions with their own shapes, dusts as one pile shape, relics/wish orbs/parts, future-world skins, gun/dino/other model skins | 46 + ~15 + ~30 + ~140 + ~45 |
| 7 | t_cube, t_ships, t_vc, t_cfb, t_en, t_stell, t_big | Other mods, as replacements in the Reason look pack: Cube Animals (CC0, may build on its pixels), Shippy Ships sails, Vanilla Constructs buckets and tools (MIT), Cooking for Blockheads oven fronts, Eternal Nether withered blocks (MIT), Stellarity items, Simply Tents and Knight Armor icons at 16 px. ARR and limited licences get fresh paintings only | ~360 |

Order: 2 first (the biggest and most seen), then 3, 6, 4, 5, 7. Each patch is its own PR and must pass the art check
on every texture it writes. Patches 2 to 5 touch only Kingdoms generators, so two can run side by side only if they
don't share a script.
