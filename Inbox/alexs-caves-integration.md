---
status: open
for: coordinator
from: Claude on the PC (Andy, 2026-11-24)
asked: 2026-11-24
thread:
claimed_by:
pr:
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/compat/AlexsCavesCompat.java (new)
  - kingdoms/src/main/resources/assets/alexscaves/ (texture overrides)
  - kingdoms/src/main/resources/data/alexscaves/ (worldgen, loot and recipe overrides, behind load conditions)
---
# Alex's Caves in the pack, reshaped to our lore

Andy is adding **Alex's Caves Continued 1.1.3** (Fabric 26.3 port by Codx, LGPL-3.0; jar in
`C:\Users\toopo\OneDrive\Desktop\Reference Mods\alexscaves-1.1.3-fabric+26.3.jar`). Andy wants its six cave biomes
reworked to fit our world. Split it into tracks when it's picked up; it's too big for one PR.

**Andy's words (2026-11-24):** "rubber folk lore can change to hail from caves like the candy cavity, however they all
come from the tinkerer's experiments. the cave maps should lead to places like mohknee if possible i'd like parity
between the magnetic cave biome and the aether magnetic biome, in terms of blocks. i dont like the mod's red and blue the
primordial caves can be a place where ancient warrior born still wrestle the dinos, like they have forever, only being
lost to the underground after a large flood brought upon them by the truth the luxtructosaurus should be a demon
entitity that also spawns in the nether after your encounter in the cave, the radiation cave can work with the aether
realm as well, and it could give the middle stoneborn variant different and imo better lore. they can be the only ones
that can tolerate the biome, them and legendary warrior born that are increased by being irradiated likewise, working
the nuclear energy into their kit/ lore. the nuclear bombs can work into the steampunk side. as their idea of forbidden
technology. it only becomes usable in the future. while the furnaces exist in our world. the abyssal caves can be
tideborn eel and manta ray inhabited. extrememly hostile. remove the raygun and other future items from the medieval
time period"

**Andy's answers to follow-up questions (same day):**
- The "middle Stoneborn variant" is the **Obsidian-hided** look (`HeritageVariants.Variant.OBSIDIAN`).
- "Cave maps lead to places like Mohknee" = **the cave biomes sit mostly under far lands like Mohknee**, so the
  maps send travellers there.
- **Future items (removed from the medieval world, found only in Los Perrito):** Raygun, Holocoder, Remote Detonator,
  Hazmat suit (mask, chestplate, leggings, boots). The Nuclear Bomb is the steam towns' forbidden tech and works only in
  the future. The Nuclear Furnace stays in our time.
- **The Submarine stays.** "the sub can stay, the model should be interpolated for the space pod as well" (the Z
  Fighter space pod: `PodModel` / `PodRenderer` in Powers).
- **Abyssal Chasm:** keep Alex's Deep Ones (and Knights and Mages) **and add** extremely hostile wild Tideborn of the eel
  and manta looks (`MORAY`, `MANTAWING` in `HeritageVariants`; their parts are in client `SeaParts`).

**What's wanted, biome by biome:**
- **Candy Cavity:** where the Rubber-folk come from: one of the Mad Tinkerer's experiments. Rewrite their lore line in
  Lore.md (now "born at the travelling fairs"); the fair still raises them.
- **Magnetic Caves:** use the same blocks as the Aether's magnetic biomes (`magnetic_scar`, `rustworks`: ferrosite,
  ferrosite sand/mud, wisproot, `FerrositeSpireFeature`, magnetic shrooms), **with no scarlet/azure red and blue**.
  Either retexture Alex's neodymium in our palette or swap its blocks for ours in its worldgen.
- **Primordial Caves:** ancient Warrior-born still wrestle the dinosaurs there, as they always have. They were lost
  underground after a great flood sent by **the Truth**.
- **Luxtructosaurus:** a demon. After a player's first meeting with it in the cave, it also spawns in the Nether (lore
  section: "The Nether and the demons").
- **Toxic Caves:** tie them to the Aether's **Irradiated** highland (the Ambrosium sickness that leaks round the
  Irradiated Giant's monolith). Only **Obsidian-hided Stoneborn** (new lore for that look) and **legendary
  Warrior-born** (`ZFighterLegendary`) can stand the biome, and radiation makes the legendary ones stronger. Work nuclear
  energy into their kits and lore.
- **Abyssal Chasm:** Deep Ones, plus the hostile eel and manta Tideborn above.
- **Future items:** as listed above. Use loot, recipe and trade removal plus a check that the item works only in Los
  Perrito, where they turn up as loot.

**Where to start:** `Lore.md` (sections: The Mad Tinkerer, The sky: the Aether and the remnants, The Nether and the
demons, The future: Los Perrito, The peoples; the Irradiated Giant paragraph near line 2452). Then `HeritageVariants`,
`ZFighterLegendary`, the Aether biome JSONs in `src/main/resources/data/jaymospowers/worldgen/biome/`, and `KitchenCompat`
as the pattern for an optional-mod compat class. Write every piece's lore as `(new)` paragraphs for Andy before building.

## Notes
- **Needs `codxlib` >= 1.3.6** (a required library, not in the Reference Mods folder). Andy has to get it from its
  official page.
- **Art license is unchecked.** The jar only carries LGPL-3.0 (code). Before copying or adapting the Submarine model
  for the space pod, or reusing any of Alex's textures, find out whether the assets are covered. If not, draw our own
  model after the sub's shape.
- Compatibility scan (Claude on the PC, 2026-11-24, from reading the jar, no game run): it has about 145 mixins. One
  real overlap: Alex's `FoodPropertiesMixin` redirects the `FoodData` call inside `FoodProperties.onConsume`, and
  Kitchen's `StomachFoodMixin` injects at its TAIL. They should both work. Camera, fog, sky and worldgen overlaps (Chrono
  and Blindness camera mixins, Tectonic) need a boot test on the Deck. Watch the Deck's memory: the jar is 76 MB with
  big mobs and terrain-wrecking nukes, and the Deck server has been earlyoom-killed before.
- Alex's Caves has **no airships**. Kingdoms already has its own (`SteamAirships`, `SkyShips`, `AirPatrols`,
  `SkyWrecks`).
- Not decided: whether Alex's **Game Controller** counts as a future item (Andy didn't pick it).
