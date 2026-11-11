# Divine-born: the feather sense, Alight, hover, and staying at the Judge
Andy: "Divine born should be like z fighters but without the raging raw ki, it is controlled and elegant, they gain a
web like sense that allows them to dodge incoming attacks and counter, as long as they have feathers. They can directly
replenish feathers by using their heritage key."
- **The sense** (`divine/DivineSense`): every Divine-born, Z Fighter or not. A melee blow or a projectile that would
  land misses instead, for 2 feathers, and they drift a step aside. Nothing is pressed. Not more than once in 10 ticks,
  and not at all once the feathers are spent. Explosions, falls, fire and the like still hurt. A web slinger keeps the
  web sense instead.
- **The answer**: for 2 seconds after a slipped blow, their next melee hit on whoever threw it does 1.5x and gives
  2 feathers back (`DivineCounterMixin`).
- **Alight** (the heritage key `;`, sent by Kingdoms): they rise a little, hang still, feathers of light wind down round
  them and the row refills, one half-feather every 4 ticks (every 2 under open sky or in the Aether). Sneak, the key
  again, being hit or a full row ends it; 20 s before the next. The sense is shut while it lasts.
- **Hover**: Jump pressed again in the air and held slows the fall to a drift, for a half-feather every 8 ticks, with
  no fall damage (`client/divine/DivineSenseClient`).
- **Staying**: one of the dead whose page weighed just gets a new choice at the Judge, "Ask to stay". Saying yes ends
  the stay among the dead (no hold at the hall, no road home, no tournament), keeps the page's weight, and makes them
  Divine-born through Kingdoms. They are free to walk the Aether.
- All numbers are constants at the top of `DivineSense`. Compiled; not played.
