---
name: valor-design
description: Valor (Jung's idea, Will-related anti-magic mechanic, haki-like but original): design list awaiting Andy's ticks, not built yet (2026-10-29)
metadata:
  type: project
  modified: 2026-10-29T22:41:18.219Z
---
2026-10-29 Andy + Jungxvv: "Valor", a way to fight magic users, unique per person (Stand-like), villagers with Valor tell
how they earned it, grows from feats. Jung's arts: Valoric Reinforcement (Brute/Warlord, forearms, resistance while
Valor lasts), Valoric Armament (Brute/Warlord/Thief, forearms+weapon, damage+stun), Valoric Senses (Archer/Marksman/
Survivalist, auto-dodge, see through walls), King's Valor (weaker foes in radius downed 10 s), King's Valor Coating
(soul colour, instakill weaker, two coatings clashing make weaker nearby faint/tremble). King's Valor only from defeating
bosses/kings; most kings spawn with Valor, rare few with King's Valor.
Design doc (Claude Doc, tickable): https://claude.ai/artifact/XBvNrzXm4GhFWfZbkD7hQ2. Proposed: awakening needs Strength 5 + a Will perk + a defiance feat;
ranks Spark/Kindled/Tempered/Steeled/Unbroken; Valor pool drains while coated; origin/look/quirk/name per person;
not points-bought, not inherited; villager "Ask about their Valor" like GiftTales; build in 3 PRs.
2026-10-29 23:02Z Andy ticked EVERYTHING and took all suggested answers (powered players half growth, Mages/Sorcerers none;
N toggle / hold N King's Valor; hits players; No Mercy exception kept; villager rarity) EXCEPT: Senses at Unbroken DO
reveal disguised vampires. Lore: Valor is common (so many folk survived great danger), never looked down on; villagers
who know no better treat you like royalty if you prove you have it. Building on branch claude/project-thread-i99guh.
PR #694 MERGED 2026-10-30 00:0xZ as pack 0.18.8 / Kingdoms 0.7.361 (#688 took 0.18.7/0.7.360; branch claude/project-thread-i99guh): Kingdoms Valor, ValorFolk,
ValorKings, client ValorClient/ValorScreen; Powers core/wounds/ValorSkill. Valor key = \ backslash (Andy 23:55Z; N was taken by
horse whistle and Web mantle). Built, not playtested; jars in /mnt/project-files/valor/.
2026-10-30 Andy asked for wings: origin SKYWARD (ValorWings/ValorWingsLook): double-tap jump flight from Kindled on the pool, wings fade with pool, wrap as armour when coated, flap-counter blocks. PR #747 MERGED 2026-10-30 16:59Z (pack 0.18.81 / Kingdoms 0.7.410; next free 0.18.82 / 0.7.411); wings wispy, all designs wrap like Wyrm (Andy). Same PR: 4 wing designs from our dragons (wyrm/notus/zephyrus/eurus, tools/gen_valor_wing_designs.py, Data.wings) and Andy's rule (2026-10-30): Valor won't bind to ki (Z Fighter), mana-attuned, Mage/Sorcerer or villager casters (Valor.unbound, dormant if already held); other powers 1/4 growth, half pool, cap Kindled.
2026-11-03 Andy: wings looked like they grew through the chest (the coated cloak wrap closed across it) and should wrap the arms like a coating, unfurling to reveal. PR #863 MERGED 2026-11-03 ~18:10Z (branch claude/project-thread-l2wx9b, Kingdoms 0.7.480): at rest wings are sleeves on the arms (SleevesModel, wings_sleeve_<look>.png from tools/gen_valor_wing_sleeves.py); flying/flap unfurls from the back; never wrap across the chest again. Jar /mnt/project-files/valor-wings-arms/.
**How to apply:** lives beside Kingdoms `Will`. Next free after #694: 0.18.9 / 0.7.362.
Related: [[will-tree]], [[player-classes]], [[downed-self-rise]].
