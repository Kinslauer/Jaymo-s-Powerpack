# Clouds and Bits plan

Andy's 29 picks (2026-11-18) from the pick list built off two mods he linked:
- **More Cloud Layers** (armaninyow, CC0, Fabric 26.3). Not shipped: it draws vanilla's cloud sheet extra times, and the
  pack never draws vanilla's clouds (Cirrus cancels `CloudRenderer.render`; Kingdoms' `CloudDecks` hides them too). Its
  ideas go into our own `CloudDecks`. CC0, so its code may be copied.
- **Bits and Balance** (Pockett-Dev, All Rights Reserved). Ideas only: our own code and names.

Pick list: https://claude.ai/artifact/L1xYUoFsAxHAYWq2dMZke8 (db `picks/andy`). Row ids are in brackets.

Every patch: tuning numbers as `static final` constants at the top of its class, player text in a lang part, a change
note per mod in `changes/<mod>/clouds-bits-<patch>.md`, never `version=`/MODLOG/codemap. Check every Minecraft API
against the 26.3 sources. New keys default to unbound unless said otherwise (R/G/V/B/C/Z/H/' are taken).

---

## Patch 1: Sky decks (Kingdoms, `client/disasters/CloudDecks`)
- **Lumpy tops** [cl-bumps]: some field cells (a hash of cell x/z) draw 1-3 extra slices above the deck's top, so the
  bright tops rise in mounds. Fewer bumps in fair weather, more as rain gathers.
- **A different sky each day** [cl-daily]: each deck's height (within +-12 blocks of `DECK_HEIGHT`), drift speed
  (x0.7-1.4) and slice alpha (x0.8-1.15) are rolled from the world seed and the day number, eased over a minute at dawn
  so nothing jumps.
- **Gaps** [cl-thin]: fair weather cuts clear patches (a second low-res mask, rolled per day); storms close them.
- **High wispy deck** [cl-third]: a third deck at about 236, one or two slices, faint (alpha ~0.18), 1.8x drift, white
  by day, warm at dusk through `SkiesClient#glow`. Off in low-end mode.

## Patch 2: Building blocks (Powers)
- **Vertical slabs and quarter steps** [bb-vslab]: every block our layer blocks cover gets a vertical slab (facing,
  two can share a block like slabs) and a quarter step (four combine into one block). Cut on the Saw and the
  stonecutter. Models need `#variable` textures (26.3).
- **Mixed slabs** [bb-mixslab]: placing a different slab into a half-full slab makes a mixed double slab block entity
  that keeps both; mining breaks only the half you look at; lanterns, chains and hanging signs can hang under any top slab.
- **Doors** [bb-doors]: a door opens its paired neighbour too (sneak for one; vanilla, Macaw's and Spooky doors);
  doors, fence gates and pressure plates can be waterlogged.

## Patch 3: World bits (Powers)
- **Ores in stone** [bb-ores]: andesite/diorite/granite/tuff variants of coal, iron, copper, gold, redstone, lapis,
  diamond, emerald, dropping and smelting as the plain ore (tags `c:ores/*`, Tinkers smeltery). Big coal veins in
  andesite (shallow) and tuff (deep, a few coal blocks); Nether gold and quartz veins with the odd raw gold block.
  Worldgen data, new chunks only. Lore: the miners' mixed seams (already canon).
- **Bottled cloud** [bb-cloudbottle]: an empty bottle used on cloudbank or aercloud, or anywhere in the Cloud Sea or
  high sky bands, gives a Bottle of Cloud. Used on a block it places a short-lived cloud puff (2x1x2 of a temporary
  cloud block, soft like cloudbank, thins away in about 20 s); used in mid-air it catches you below your feet once.
  Lore paragraph (new).

## Patch 4: Moving about (Powers + Kingdoms)
- **Late jumps** [bb-coyote]: jump still works 4 ticks after walking off a ledge (2 in heavy armour); holding jump
  repeats jumps under a low ceiling. Powers' freerun client moves.
- **Auto walk** [bb-autowalk]: unbound key, walks forward until you press forward/back or open a screen.
- **Pull riders off** [bb-dismount]: sneak + right-click a mount, cart or boat to take its passengers off (never the
  driver).
- **Knocking** [bb-knock]: left-click a closed door with an empty hand to knock (sound, no damage). Kingdoms: the MCA
  villager whose home it is comes to the door within 20 blocks; at night only if they like you; guards inside a keep
  come instead.
- **Don't hit your own pets** [bb-pets]: your attacks pass over your own tamed animals and Kingdoms companions; sneak
  attacks still land.

## Patch 5: Mobs (Kingdoms + Powers)
- **Golems vs creepers** [bb-golem]: iron golems and town golems target creepers and kill them without setting them
  off (their hit stops the fuse).
- **Creepers burn by day** [bb-creepersun]: like zombies, unless in shade, water or wearing a helmet.
- **Cave spiders underground** [bb-cavespiders]: below y 40 and out of skylight, 1 in 4 spider spawns is a cave spider.
- **Phantoms carry you off** [bb-grab]: a phantom's swoop on a player in the open can grab (1 in 5), climb and drop
  them after 3-6 s or when hurt for a third of its health; the carried player deals double damage to it and can
  sneak to wriggle free early (fall damage applies). Uses the giant grab's carry plumbing where it fits. Lore (new).
- **Deeper monsters, older gear** [bb-depthgear]: hostile humanoids spawning below y 0 roll better gear the deeper
  they are (rusted iron, then Tinkers-forged iron parts); stacks with world tiers.

## Patch 6: Fighting (Powers)
- **Snowballs sting** [bb-snowball]: 1 damage and a short frostbite chill; 3 against Nether creatures and Emberborn.
- **Hits from different sources** [bb-iframes]: damage of a different kind (fire, bleed, magic, melee, projectile)
  lands during the usual 10-tick immunity; the same kind still waits.
- **Pick your arrow** [bb-arrows]: scroll while a bow or crossbow is drawn to pick which arrow kind it fires (quiver
  first), saved on the weapon; shown beside the hotbar.
- **Tipped arrows leave a cloud** [bb-tipped]: a small 1.5-block lingering cloud of the arrow's potion for 3 s.
- **Keep some XP** [bb-keepxp]: you keep 40% of your levels on death (gamerule keepInventory off only).
- **No one-hit deaths from full health** [bb-oneshot]: a blow that would kill from full health downs you instead (or
  leaves you at 1 health where downed doesn't apply).

## Patch 7: Screen and chat (Powers client)
- **Biome name** [bb-biometitle]: entering a new biome shows its name small in the top corner for 3 s, once per biome
  per 5 minutes. Place names only.
- **Use bar** [bb-useticker]: eating, drinking, drawing a bow and other use actions show a thin bar under the
  crosshair in our charge-bar style.
- **Chat** [bb-chat]: @name mentions highlight and ping the named player; Shift+T over an item in a screen shares it
  in chat with its hover tooltip.
- **Small touches** [bb-clientbits]: night vision fades out over its last 10 s; soul fire burns blue on you; your held
  item stays visible in first person while riding.

## Order
Tracks run two at a time on one branch (`claude/project-thread-f0jf19`), one commit per patch:
1 + 2, then 3 + 4, then 5 + 6, then 7. Each gets an independent verifier.
