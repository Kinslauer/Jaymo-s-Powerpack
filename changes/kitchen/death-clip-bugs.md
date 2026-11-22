# Zombies go for shade by day instead of feeding on the dead
- A zombie (anything that burns in the sun) no longer gnaws a body, eats torn meat or gut piles out in the sun while
  the sun burns; it goes for shade (Jaymo's Powers' shelter search). Its gnawing ran at the same goal priority as the
  shelter search, and a running goal is only cut off by a higher one, so a body held it there burning
  (`Scavengers.sunlit`). In shade, or with a helmet on, or in rain, it still eats. With Jaymo's Powers, "in the sun" is
  Powers' own ray test, the one the sun burns by (leaves don't shade: `Scavengers.setSunTest`); without it, open sky.
- Built by CI only; not played.
