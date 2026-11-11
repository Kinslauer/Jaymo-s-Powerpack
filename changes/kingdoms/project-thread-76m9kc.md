# clash sparks, more shield bearers
Andy (2026-11-11): "some of the fighters should have shields so the fight looks realistic", with sparks from clashes and parries.
- **Blade clashes and fighters' parries** (`BladeClash`, `MobParry`) throw Jaymo's Powers' new shower of sparks (chips of
  wood when a fighter parries with a wooden shield), through `PowersCompat.clashSparks` and `guardMet`. Without Powers the
  old vanilla sparks stay.
- **More shields in the wild** (`ShieldBearers`): 45% of barbarians carry a plain plank shield, 40% of orc grunts one
  daubed black or blood red, 25% of goblins a plain one, each beside a one-handed weapon. They keep them as MCA villagers.
- **The risen dead** (`MonsterGear`): an armed zombie's shield chance per world tier goes from 5/12/20/30% to
  12/22/32/42%, and a skeleton that traded its bow for a blade may carry one too. Never beside a trident or a bow.
- **Shields are raised more** (`MobShields`): 65% of chances instead of 55%, with a shorter rest between raises (20 to 50 ticks, was 25 to 60).
- Compiled; not played.
