# Artifacts baubles

Andy asked (2026-11-04) for the [Artifacts](https://www.curseforge.com/minecraft/mc-mods/artifacts) mod's baubles and for a way to get them that fits the pack.

**Licence.** Artifacts (Wouter Pauwels, github.com/ochotonida/artifacts) is MIT. Its 26.3 branch has no release yet, and on Fabric it puts its items in Trinkets' slots, which would give us a second accessory screen. So its 45 wearables are ported into our own Baubles instead. Most item textures and the cushion sounds are Artifacts' own, used under MIT (notice in `textures/item/bauble/artifacts/LICENSE.txt`). The effects are our own code (`baubles/artifacts/`). The umbrella, everlasting foods and mimics are left out, since they aren't baubles.

## Slots
A ninth slot, **Feet**, sits in the bottom left corner of the player box on the bauble screen.

| Slot | Baubles |
|---|---|
| Head | alewife's cap, jester's cap, reed breather, owl mask, villager hat, soothsayer's hood, rider's hat, fisher's hat |
| Amulet | lucky scarf, scarf of invisibility, cross necklace, panic necklace, shock, flame and thorn pendants |
| Charm | charm of sinking, charm of shrinking, chorus totem, crystal heart (also belt) |
| Belt | cloud in a bottle, obsidian skull, antidote vessel, jester's cushion (these four hang at the hip), lodestone, crystal heart, wind bladder, pearl pouch |
| Wrist | digging claws, feral claws, iron knuckles, fire gauntlet, ram's-head bracer, vampiric glove, golden hook, smelter's bracer, withered bracelet |
| Ring | glutton's ring |
| Feet | waterstrider boots, hare's-foot boots, kitty slippers, courier's boots, snowshoes, steadfast spikes, frogskin flippers, rooted boots, strider shoes |

Medieval pass (Andy, 2026-11-04): anything that looked modern got a medieval stand-in with the same effect: the plastic and novelty drinking hats became the alewife's cap and jester's cap, the snorkel a reed breather, the night vision goggles an owl mask, the superstitious hat a soothsayer's hood, the cowboy hat a rider's hat, the universal attractor a lodestone, the helium flamingo a wind bladder, the warp drive a pearl pouch, the power glove iron knuckles, the pocket piston a ram's-head bracer, the pickaxe heater a smelter's bracer, the onion ring a glutton's ring, the running shoes courier's boots, the aqua dashers waterstrider boots and the flippers frogskin flippers, each repainted (`tools/artifact_medieval_assets.py`). The angler's hat, whoopee cushion and bunny hoppers keep their art as the fisher's hat, jester's cushion and hare's-foot boots.

Changes from Artifacts: no durability (as Artifacts' defaults), no toggle keys. The scarf of invisibility works while you sneak. The owl mask gives Artifacts' faint sight (15%), so nights stay dark. The chorus totem is used up when it saves you.

## Getting them
Found only, never crafted. Each bauble has homes (`ArtifactLoot`), and a chest from a home may hold one bauble that lives there:

| Home | Places (chance per chest) | Baubles |
|---|---|---|
| Underground | dungeons 12%, mineshafts 5%, stronghold halls 8%, trial chamber common vaults 4% | claws, gauntlets, scarves, hoods, boots, lodestone, antidote |
| Towns and camps | village houses 3-6%, outposts 8%, abandoned camps 8% (secret chest 25%) | villager hat, alewife's and jester's caps, glutton's ring, boots, jester's cushion, rider's hat |
| Water | buried treasure 15%, shipwreck treasure 12%, ocean ruins, fisher houses, fishing treasure 5% | reed breather, frogskin flippers, waterstrider boots, fisher's hat, charm of sinking, golden hook, wind bladder |
| Nether | bastion treasure 20%, other bastion chests 8%, fortresses 10%, ruined portals 5% | obsidian skull, fire gauntlet, flame pendant, strider shoes, withered bracelet, smelter's bracer, golden hook, vampiric glove |
| Cold | igloos 25%, snowy and taiga houses, ancient city ice box 10% | snowshoes, steadfast spikes, feral claws, cloud in a bottle |
| Desert | desert pyramids 12%, desert houses, desert archaeology | rider's hat, cross necklace, antidote vessel |
| Jungle | jungle temples 20%, trail ruins archaeology 5% | thorn pendant, panic necklace, antidote vessel, feral claws, ram's-head bracer, rooted boots |
| Deep | ancient cities 12%, mansions 15%, stronghold libraries 10%, trial chamber rare/unique and ominous vaults 10-20% | crystal heart, scarf of invisibility, soothsayer's hood, lucky scarf, iron knuckles, vampiric glove, shock pendant, cloud in a bottle |
| End | End city treasure 15% | chorus totem, pearl pouch, charm of shrinking, cloud in a bottle |
| Cats | a cat's morning gift 2% | kitty slippers |

Chests from other mods' structures (castles, Lost Cities, Repurposed Structures, Aether ruins) hold any of the 45 at 2.5%.
