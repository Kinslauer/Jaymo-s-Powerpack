# JEI shows stone tools, hammer and light armour recipes on a server
- **Missing recipes in JEI**: the stone-age tool recipes, the hammer recipes (crushing ore) and light armour now show
  in JEI on a server. JEI only asks the server for Minecraft's own kinds of recipe, so these never reached the players;
  `StoneAge`, `OreWashing` and `LightArmour` now have the server send them (`RecipeSynchronization`).
- Built on CI only; not played.
