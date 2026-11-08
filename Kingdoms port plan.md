# Kingdoms port plan: Inhabitants and Stormborn Tomb

Andy, 2026-11-08: Inhabitants (JeremySeq, MIT, Forge 1.20.1) and Dungeon Conquest: Stormborn Tomb (Tada_no_ahiru, MIT,
Fabric 1.21.1) become Kingdoms additions. Neither they nor Stormborn's required mods have 26.3 builds, so both are
ported. One Inbox task per feature. Licences: Inhabitants, Stormborn, DungeonZ, FrogiLib are MIT (reuse with
credit); Draugr Invasion is All Rights Reserved and Supplementaries has its own licence: nothing from either.

## Posted (startable now)
- [ ] `kingdoms-geckolib-groundwork` (Claude session): GeckoLib in Kingdoms, the example mob pattern, credits.
- [ ] `bogre-model` (hermes-modeller): Bogre model, texture, animations from Inhabitants, render-checked.
- [ ] `stormborn-tomb-structures` (hermes): the tombs, blocks swapped, worldgen.
- [ ] `tomb-no-build-rule` (hermes): no breaking/placing inside tagged dungeons.

## Waiting (post when what they depend on is merged)
- [ ] `bogre-mob` (Claude session, the first real mob: sets the pattern): needs groundwork + bogre-model. Mangrove
      swamps, avoids company.
- [ ] `bogre-cooking` (hermes): serve ingredients, food with side effects; Kitchen ingredients through KitchenCompat.
      Needs bogre-mob.
- [ ] `bone-club` (hermes): 3 bone blocks to a Bogre, Giant Bone Club, needs Strength to wield. Needs bogre-mob.
- [ ] `impaler-model` (hermes-modeller): 4 variants. Needs groundwork.
- [ ] `impaler-mob` (hermes): below deepslate, scream drops stalactites, spikes. Needs impaler-model.
- [ ] `impaler-spikes` (hermes): concussive arrows, handheld drill. Needs impaler-mob.
- [ ] `warped-clam` (model: hermes-modeller, then mob: hermes): End, launch on step, pearls with a brush, shovel
      pick-up. Needs groundwork.
- [ ] `tomb-undead` (model: hermes-modeller, then mob: hermes): our own Draugr-style dead on the Hollows, not copied
      from Draugr Invasion. Needs groundwork + tomb structures.
- [ ] `tomb-boss`: design with Andy first (Stormborn's pages don't name theirs), then model + mob.
