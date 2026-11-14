# clean kills and skinning in strokes
- Andy: "Yeah 1 and 3" (two ideas left over from a "Carcass Craft cleanroom" zip he sent; nothing is ported from it).
- **Clean kills** (`corpse/CleanKill`): how an animal died decides what its carcass gives. A blade, an arrow or an
  armed mob's weapon leaves it whole, as before. **Mauled** (a wolf, a bear, your own dog: any mob with empty hands):
  the hide is torn, half the skins are lost (a single hide is lost). **Burnt** (died on fire or by fire): no hide.
  **Battered** (a fall, a blast, bare fists, or a weapon in the new item tag `jaymoskitchen:bruising_weapons`: maces,
  hammers, cudgels, staves): a third of the meat is lost, never the last piece. The loss is taken from the body's loot
  when the body is made, so the ground, the butcher block, the hook and butcher villagers all see the same carcass.
  The first click on such a body with a butchering tool only tells you what happened to it
  ("Mauled: the hide is torn"); the next click starts the work.
- **Skinning in strokes** (`corpse/Hanging`): the skin comes off in 3 knife pulls instead of one click, each wearing
  the knife ("Skinning: pull 1 of 3"). A chicken or rabbit takes 2, a big carcass 5, and a flint or bone knife
  (new tag `jaymoskitchen:poor_knives`) 2 more. The skins and scraps still come on the last pull.
- **The hook is for the skilled** (Andy: "only the skilled can do it at all everyone else uses the buther table and
  now gets less returns"): `Hanging.setButcherSkill` lets another mod say how practised a butcher a player is
  (Jaymo's Kingdoms' Butcher's Hand ranks; with Kitchen alone everyone counts as 1). At 0, a carcass on a meat hook
  can't be worked ("You don't know the hook's work. Take it to a Butcher Block"), and a big carcass can't be bled
  where it lies. Each point past the first is a skinning pull fewer (never under one). At 3, the first click on a
  mauled or battered body puts back what was lost ("You trim round the damage: nothing is lost"); a burnt hide stays
  lost. What was lost is saved with the body (`Spoiled`).
- **The butcher block gives less**: just what the animal would have dropped. The extras (1-2 more meat and
  `ButcheringBonus`'s hides, bones and rare finds) now come only off the hook, or from a butcher villager. The
  ground is still about half.
- **Deer dropped venison the moment they died** (Andy): Cube Animals' 27 animals throw their death loot with
  `LivingEntity.drop`, which adds the item to the world directly, so the body never got it. `LivingEntityMixin`
  catches it into the body now (found by reading the mod's jar; the earlier `strayCarcassLoot` guess missed it).
- **Gutting** (Andy: "Yes i do"): after the skin and before the cuts, a hung carcass is gutted with a knife ("Gut it
  with a knife before the cuts"). Out comes **Offal** (new item: 1 from a small body, 2, or 4 from a big carcass), a
  raw meat (it can make you sick raw) that cooks into **Cooked Offal** on a campfire, in a furnace or a smoker. Icons
  painted in the manner of vanilla rotten flesh (a liver lobe on the slant, a loop of gut under it). Carcasses already past skinning skip it.
- **The work shows** (Andy: "Start work on 1 and 2"): a carcass skinned on the hook is drawn as bare red flesh with
  streaks of fat, and once gutted its trunk is a dark hollow crossed by ribs. `client/corpse/Dressed` paints a copy of
  the animal's own texture (per model, texture and state), so it works for any animal's model; the body's renderer
  draws with it through `LivingEntityRendererMixin`, after `ClosedEyes`. The trunk is the part named body, torso,
  tulow or chest; a model with none shows no ribs. Synced as `CorpseEntity.dressed()`, saved as `Dressed`. A carcass
  with no skin to take (or a burnt one) is never drawn skinned.
- **Offal has uses**: held in the other hand it baits a fishing rod like Fish Bait (`fishing/Bait`); dogs take it
  as food, raw or cooked (`minecraft:wolf_food`), and eat it off the ground; Raw Blood Sausage now needs one as its
  casing; and offal lying on the ground is a gut pile that wild scavengers (wolves, foxes, bears, zombies) smell
  from 16 blocks and come to eat (`corpse/Scavengers`), whoever dropped it.
- Only animal bodies are judged; monsters, villagers and players are as before. Bodies from before this are clean.
- Compiled with Gradle (compileJava, compileClientJava, processResources); not played.
