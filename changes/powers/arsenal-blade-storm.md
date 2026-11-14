# Arsenal: Blade Storm (flying blades, patch 3)
- **Blade Storm**: hold V and weapons whip out of a rift behind your shoulder one after another, each swinging out in a
  wide loop through what you aim at (your lock-on target first) and back in, for as long as you hold, up to 6 s. A blade
  cuts everything along its loop (3 damage, 4.5 with a relic in hand; one thing is cut at most every 0.4 s). You are
  slowed while it runs. A tap of V is still Barrage, now on letting go.
- Each Attendant Blade out quickens the storm (one blade every 5 ticks, down to every 2 with six out). Your own weapons
  and loose rocks on the wing take their turns in it. Every third blade is an echo whose loop ignores walls.
- Barrage's cooldown pays for it: 10 s plus 3 ticks for each tick the storm ran.
- The Void Arsenal (whose V is its gates) starts a 4 s storm from the Attendant Blades' order wheel; the wheel has it for
  everyone.
- Classes: `power/arsenal/ArsenalStorm` (attachment `arsenal_storm`), client `ArsenalStormRenderer`.
- Compiled; not played. Echo blades are drawn smaller and unlit, not see-through (item models have no alpha here).
