# Enemies telegraph their blows longer and walk in before swinging
- Andy: "enemies should telegraph attacks more and get closer before swinging." Every goal- or brain-driven melee mob (zombies and their kin, spiders, wolves, piglins, hoglins, unarmed or vanilla-goal illagers and skeletons, and modded mobs that attack through vanilla's melee goal) now:
  - **winds up longer**: the glint and ping come 10 ticks (half a second) before the blow, up from 6;
  - **holds its arms up** through the wind-up (zombies, skeletons, illagers show their raised-arms pose) and **swings its arm when the blow falls**, not with the glint (it used to finish its swing before the hit);
  - **steps in** a little 3 ticks before the blow;
  - **starts the wind-up only from close in** (55% of its reach past its body), so it walks up to you instead of glinting from the edge of its reach and whiffing. The blow still lands anywhere in its full reach; a spear-like weapon with a near limit keeps vanilla's reach, and so do riders.
- Damage, attack rate (the cooldown still starts with the glint) and the parry/heavy rules are unchanged.
- `AttackTells` (`WINDUP`, `START_REACH_SHARE`, `LUNGE_LEAD`, `LUNGE_SPEED`), `AttackTellGoalMixin`, `AttackTellBrainMixin`.
- Built; not played.
