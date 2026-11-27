# NPCs take a second to think before setting on a player they've just picked out
- **The extra second** (Andy: "npcs across the board should think for an extra second to give stealth users the required
  time to close in for a stealth takedown"): every mob that picks out a player it would fight (any target goal or brain
  attack sensor, through `TargetingConditions.test`) now spends 1 s sizing them up before it can take them as its foe.
  This includes monsters, animals, the folk, MCA's guards and bosses. All that second it isn't after them, so it still
  counts as unaware for a stealth kill, a sneak attack or an assassination. It doesn't turn round either: the takedown
  from behind still works. If it still has them after the second, it sets on them as before. Not picked out for 2 s,
  they're sized up afresh next time.
- **No second when**: it's already after them, a friend just told it where they are, or they just hurt it (a blow is
  answered at once). Creative and spectator players are untouched.
- **You can see it**: the awareness eye fills over the second, and the spectate dev view's top line reads "Thinking:
  sizing up <player> (0.6 s left)" in yellow.
- `ThinkFirst` (new), `mixin.TargetingThinkMixin` (new, combat mixins), `Senses` (gauge), `NpcFocus`, lang part `npc-eyes`.
- Built; not played.
