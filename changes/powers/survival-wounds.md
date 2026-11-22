# Survival picks: wound pools, head blows, headshots, armour per part, medicine over time
- **Own pools on top of health** (Andy's LSO picks): head 8, body 12, each arm 8, each leg 12, each foot 8 on 20 health
  (shares 0.4/0.6/0.4/0.6/0.4; animals, bugs and fliers the same way). A hit takes its damage from your health and the
  same from the part it lands on; food and potions heal the health, not the parts. Fire, magic, poison, drowning and
  blasts wear every part by the share they take off your health (never below 5%, so they never black out or break a part).
- **Downing:** health at zero, or a hit that blacks out your head or body, downs you. Only a blow that lands on your head
  and blacks it out while you're up kills outright (not from full health: that still downs). Limbs at zero break as before.
- **20% left:** one arm Mining Fatigue, both arms Weakness, a foot Slowness, the body takes 1.25x damage, a leg makes falls
  1.5x, the head aches (a throbbing pale haze). Painkillers (willow bark, poppy, leeches, drink) and adrenaline hold them off.
- **Broken hearts:** every part at zero takes 10% off your max health until it heals (at most 50%).
- **Headshots:** an arrow or other shot to a bare head does double damage (a Ranger's arrow goes through the helmet).
- **Armour per part:** each piece cuts what lands on its own part, 10% for diamond, scaled by its armour.
- **Medicine over time:** linen bandage 3 over 15 s and dresses three wounds per bandage; rag, clean rag and grass
  bandage 3 over 20 s; yarrow powder also heals 2 over 30 s; bone broth heals 5 into every part over 10 s. Medicine heals your health
  by each point's share (all pools from nothing = full health). Healing a part costs 0.5 food exhaustion per point. Parts mend by themselves while fed (7 hunger or more), a whole part in 10 minutes
  (a quarter as fast until patched up). A night's sleep heals every part as far as it can.
- The Hermit Bean heals the parts too. Poppy syrup kept as it was (6 minutes, addiction).
- Classes: `Wounds`, `BodyPlan`, `HitRouting`, new `PartStrain`, `Treatment`, `Herbal`, `Downed`, `PainHud`.
- Built in CI; not played. Not checked in game: how often mob melee lands on the head, the headache haze's look, the
  broken-hearts max-health drop on mobs, the bandage's dressing count across relogs.
