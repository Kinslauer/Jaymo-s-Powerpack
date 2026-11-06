---
name: equestrian-plan
description: Jaymo's Equestrian (our own SWEM, in Kingdoms): stages 1-2 merged (#58, #66); stage 3 Kingdoms hooks PR #74 (Kingdoms 0.5.45 / pack 0.8.88)
metadata:
  type: project
  modified: 2026-10-12T17:00:00.885Z
---
2026-10-12: Andy liked SWEM (Star Worm Equestrian; Forge/NeoForge 1.21.1 only, can't be used) and asked for our own version.
Plan doc (Claude Doc, Andy edited it): https://claude.ai/code/artifact/442ab445-11f7-4871-bdd0-834eafdb60b8
Stages: 1 riding (6 breeds destrier/courser/palfrey/draft/steppe pony/desert horse, hidden stats speed/stamina/jump/courage/temper
inherited by foals, 5 gaits on scroll wheel + sprint burst, stamina bar bottom-right Wind style, courage spooking, new original coats),
2 bond and tack (trust, discipline levels, tricks, 3D tack, care, hunger and thirst, shoes), 3 Kingdoms hooks (stablehand fees,
markets, farrier, cavalry, caravans, quests). Nothing copied from SWEM.
Andy's answers 2026-10-12 16:59Z ("take my changes and go ahead on stage 1"):
- Replace vanilla horses everywhere, skeleton and zombie horses too.
- Hunger and thirst ON (stage 2).
- Death permanent for now; keep the mane lock. Later, with a wizardry/magic power, a necromancer revives corpses and horses from locks.
- War elephant wanted ("would be incredible"); at minimum the horses in the new style.
- Built inside Kingdoms (default; Andy never objected).
Stage 1 MERGED 2026-10-12 19:30Z: PR #58, Kingdoms 0.5.34 / pack 0.8.70. Package com.jaymoskingdoms.equestrian; textures tools/gen_equestrian.py; test jar /mnt/project-files/equestrian/; op cmd /kingdom horse <breed>. Seen in game via cloud playtest (elephants/mammoths render); riding untested in game. Screenshots /mnt/project-files/equestrian/screenshots/. Stage 2 (bond and tack) MERGED 2026-10-13 00:12Z as PR #66 (Kingdoms 0.5.44 / pack 0.8.86): Bonding/Bond/Tack/TackMenu/Tackroom, client TackLayer/TackScreen, art tools/gen_tack.py; keys N whistle, J trick; saddlebags left to Kitchen. Horse owner isn't synced to clients (client-side checks use bond trust instead).
Elephant look (Andy chose "Port its look" 2026-10-12): Mo' Creatures (DrZhark, GPL-3) elephant model+hides ported, plus woolly_mammoth breed for snowy realms; licence in kingdoms licenses/mocreatures, originals in kingdoms/tools/mocreatures, hides by tools/gen_elephant_hides.py. Mo' Creatures RENEWED (LGPL, 26.2) can't be downloaded here; the old repo is on raw.githubusercontent (branch 1.10.2).
Mo' Creatures horses (Andy 2026-10-12: "these horse models are not gonna cut it"): added to PR #66. MocHorseModel/MocHorseRenderer replace vanilla renderers for horse/skeleton/zombie; coats at textures/entity/moc_horse/ by tools/gen_moc_horses.py (36 coats painted on MoC horsewhite); armour overlays by equipment asset id; TackLayer pieces on MoC bones.
Stage 3 MERGED 2026-10-13 02:37Z, PR #74 (re-versioned to Kingdoms 0.5.47 / pack 0.8.92 after merging master, branch claude/equestrian-stage1-py88rc): Stables.java; roles STABLEHAND 13 / FARRIER 14 (MCA professions); boarding attachment jaymoskingdoms:stall; quest kinds BREAK_IN 8, STUD 9, RACE 10; op /kingdom quests offer horse. Dev run includes MCA, so workers are MCA villagers. Screenshots /mnt/project-files/equestrian/stage3/. Andy asked for a GUI instead of chat (2026-10-13): StableNet + client StableScreen; right-click folk stable workers, sneak+right-click MCA ones (plain click stays MCA's screen); added to PR #74; rows draw each horse in its real coat (client-only horse entity, needs setId).
Free rein (Andy 2026-10-20 asked about Better Mount Steering, MIT, no 26.3): own FreeRein/FreeReinClient, third-person free camera + WASD turns the horse (key unbound by default), PR #348 MERGED 2026-10-21 02:5xZ as Kingdoms 0.7.110 / pack 0.15.11; also fixed LivingEntitySwingMixin descriptor. Free rein + Shoulder Surfing fix (SS coupled camera reset player yaw each tick; @Pseudo mixins client/mixin/shouldersurfing) PR #356 MERGED 2026-10-21 04:4xZ as 0.7.113 / 0.15.19 (next free 0.15.20 / 0.7.114). Kingdoms dev run can test SS: drop its jar + Forge Config API Port into kingdoms/run/mods.
Related: [[kingdoms-stables]], [[kingdoms-caravans]], [[kingdoms-rickshaws]].
