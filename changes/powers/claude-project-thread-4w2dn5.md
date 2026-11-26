# Mobs slow down under heavy weapons
- Andy (2026-11-25): "they should move slower when carrying something for example the baby zombie was sprinting ... with a giant spear in hand it just didnt make sense".
- **Heavy load** (`combat/HeavyLoad`, from `ArmourWearMobMixin`): what a mob holds weighs on it by its size. A spear, halberd or any long or two-handed weapon is a full load, a shield half, a one-handed weapon 0.3; a man-sized body bears 0.6 freely, and past that loses 30% of its speed per load more, at most 60%, and doesn't sprint. A grown zombie with a spear is about 10% slower; a baby zombie with the same spear moves at 40% of its usual pace, slower than a grown zombie walks. Players aren't affected.
- Built with Gradle; not played.
