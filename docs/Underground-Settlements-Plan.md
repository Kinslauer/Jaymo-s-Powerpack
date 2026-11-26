# Underground Stoneborn holds and goblin villages

Design for Andy's approval, November 26, 2026. Planning only: no structures or population changes ship with this document. The hold is lifted; location and Stoneborn architecture remain undecided. Follow the PR-sized tracks below after those decisions, as in Plan-0.8.

## Agreed scope and lore

Raise original Stoneborn holds by code, never by converting ordinary villages. Underground goblin villages may rise near any Stoneborn settlement there, not only kingdoms. Spider beastfolk occasionally settle in either, and only these underground settlements receive these newcomers. No ordinary villages are to be generated there. Existing settlements and residents are not retroactively converted or deleted.

The Stoneborn were raised under mountains among picks and lanterns. Their tin and deep steel give the holds a reason to exist. The silk deeps are dangerous spider nesting caves with a brood queen, not a synonym for every underground settlement. The Beneath is the pale island country below the Abyss, not the Overworld's caves; do not use it as this feature's location or recreate the removed Lost Cities layer.

Proposed placements, kept here for approval rather than editing Lore.md:

(new) **The Stoneborn holds below.** Under the mountains, the Stoneborn cut their homes beside the workings: a hall, doors in the stone, picks by the threshold and lanterns along the road. These were built by their own hands, not taken from a village above. Their smiths keep the Anvil-Father's craft. [Location wording depends on question 1.]

(new) **The goblins beside the holds.** Goblin villages gather in neighbouring caverns, close enough to the Stoneborn workings to find trade and discarded metal. Their patched huts and mushroom roofs belong to the goblins, even where a Stoneborn hold is the nearest neighbour. Nearness does not itself make either people the other's subjects or allies.

(new) **The spider kin at the doors.** Now and then one of the spider beastfolk comes out of the dark to live among the Stoneborn or the goblins below. They are neighbours with names and work, not the brood queen's spiders. A web in the deeps does not tell a traveller whether a person or a hunter lives beyond it.

## Code baseline

Paths below are relative to `kingdoms/src/main/java/com/jaymoskingdoms/`; located through `docs/codemap/kingdoms.md`.

- `Havens.draw` assigns an existing faction a haven identity; it does not construct a hold. `make` also changes nearby eligible residents. `fits` and `settle` can replace a nonmatching newcomer during the two-day settling window. Use explicit founding metadata and protect admitted spider residents; never route an ordinary village through conversion to satisfy this task.
- `HavenBuildings.hillside` is a turf-covered hillside house, not an underground design. Its `mine` is a headframe and decorative pit; the comment explicitly says the blueprint cannot dig. `HavenGrowth` supplies production and expansion rules, including mines, but does not make those designs cave-safe.
- `Camps.spawnCamps` considers realms and runs several camp types. Add a separate underground goblin path; broadening its realm filter would also invite unrelated camps. Its counts use horizontal distance, which cannot distinguish stacked caverns.
- `GoblinVillages` already retains a goblin camp identity, storage, king, economy and growth. `spot` uses a surface heightmap and has an unchecked fallback. `GoblinBuildings` includes tall gate towers, a ring and growth slots; validating only a hut or central point is insufficient.
- `Settlers` excludes barbarian factions, so goblin villages cannot receive its arrivals today. `kindFor` chooses orc/goblin, the cap counts greenskins, and proximity ignores vertical separation. Its arrival uses `ShutIn.openGround`, also heightmap-based.
- `HeritageVariants.ARACHNID` is a Beastfolk variant biased to `Land.DARK`; `Land.DEEP` exists. A terrain weight is not an exclusive settlement permission. Explicitly select the variant for approved arrivals rather than globally changing all beastfolk rolls.
- `McaCompat.settler` delegates creation to `McaBridge`; `enlist` may recruit an existing villager or create one. Trace creation, heritage initialization, faction/home assignment and recruitment before using either for a new hold. Do not enlist unrelated nearby residents.

## Track 1 — Cave-floor selection and settlement identity

**PR:** bounded cave-site search, persistent underground site identity, focused tests. No automatic founding yet. Proposed new helper names are design suggestions, not existing APIs.

Store the faction id, actual floor/seat, settlement kind and approved extent in the existing persistence style. Goblin sites also retain the source Stoneborn faction id. Eligibility uses this identity plus the approved location policy, never just a negative Y or a mountain biome at a cave point. Preserve old saves with an empty default and do not infer underground identity from existing ordinary villages.

Search only already available chunks, with a fixed attempt/block budget per tick. Scan candidate columns within the approved vertical interval and actual level bounds; consider multiple floors rather than the first roof encountered. Require solid safe support, dry feet/head space, roof cover and a connected walking route through the planned footprint. Reject fluids, dangerous floor blocks, bedrock, protected structures, occupied claims and player construction. A sturdy top alone is not a hazard check. Check the complete building volume, foundation and intended excavation shell before placing anything. Missing chunks or no valid floor means defer/skip, never force chunk generation or fall back to the surface.

Keep founding-volume validation separate from the smaller safe-standing search. Goblin births, arrivals, growth and shut-in recovery must all preserve the settlement floor and connectivity; recovery must not move inhabitants upstairs. Reuse the surface behavior for surface sites.

**26.3 source check:** read from `E:/mcsrc-26.3/common/net/minecraft/`: `world/level/LevelHeightAccessor.java` declares `getMinY()` and inclusive `getMaxY()`; `world/level/BlockGetter.java` declares `getBlockState(BlockPos)` and `getFluidState(BlockPos)`; `world/level/block/state/BlockBehaviour.java` provides `isAir()` and `isFaceSturdy(BlockGetter, BlockPos, Direction)`. These support the search specification; none promises safety or connectivity by itself. Use the project's `Loaded.made` guard before reads. Additional APIs chosen during implementation must be checked against these sources then; no Gradle source generation is needed on Andy's PC.

**Checks:** two floors at the same X/Z; flooded and lava caves; insufficient headroom; bedrock boundary; unloaded corner; player room; rejected site leaves no blocks/faction behind; repeated checks and restart do not duplicate identity. Confirm build-height limits are not mistaken for the Overworld cave band in the stacked world.

## Track 2 — Raise Stoneborn holds

**Depends on:** track 1 and Andy's location/building choices. **PR:** dedicated founding and original cave blueprints, with small integration hooks in Havens, construction and growth.

Choose a safe natural cavern with room for the approved core and expansion. Preflight the entire planned placement. Create a new settlement and explicit Stoneborn identity, register its housing, storage and work sites, then spawn its own residents. Avoid `Havens.make`'s surrounding-resident conversion behavior. Keep claims compatible with the current chunk-based model: initially reject horizontal overlap with another settlement rather than silently introducing layered ownership.

Recommend a modest core: common hall, dwellings, workshop and working mine entrance. Excavation must be bounded to approved natural rock, with explicit air placements and a checked shell against water/lava; it must not hollow an arbitrary mountain. Keep entrances and paths tall enough for MCA residents. Use existing production and costs; adapt growth to validated cave slots, including future roof clearance. Do not let ordinary surface fields, roads or mine headframes appear above the hold through a generic growth fallback. Food supply and final architecture need approval below.

Audit automatic village discovery/crowning and settlement generation entry points for the chosen band: ordinary village creation must be rejected there, not converted afterward. Check external structure placement too; identify the actual pack generators before selecting the narrow exclusion hook. Existing villagers are not erased. This acceptance requirement is not satisfied merely by adding a new hold spawner.

**Checks:** independent founding with no village nearby; all initial residents Stoneborn; existing village unchanged; no ordinary underground village generated in the selected region; reload preserves homes and storage; growth and mine access stay underground; doors and beds reachable. Validate on CI/Deck, then playtest; no local Gradle.

## Track 3 — Goblin neighbours

**Depends on:** tracks 1–2. **PR:** underground goblin selection, cave-safe layout and lifecycle.

Enumerate all standing eligible underground Stoneborn settlements, including village holds. Use a separate limited goblin spawn attempt, with an initial proposal of at most one neighbour per hold. Find a nearby connected cavern, not merely a close X/Z point through solid rock. Persist the source id for counts; reject overlap and repeat founding. Do not add orc, illager, pirate or ordinary barbarian camps to this route.

Retain goblin identity, king, stockpile and economy. Fit the full ring/towers and later growth, or supply a compact cave layout whose throne, tavern, lookout and storage coordinates are used consistently for spawning and king replacement. Do not silently clip the current towers. Update `spot` and its fallback, growth placement and any hired extras to use safe cave positions. Keep political relations as existing rules determine; adjacency alone grants no alliance. If a hold falls, stop new neighbour generation; an existing goblin village keeps its identity.

**Checks:** a village-sized hold can attract one; a surface hold cannot through this path; two vertically separate caverns do not count as neighbours; restart does not duplicate; king replacement and growth remain on the floor; surface goblin villages retain current behavior.

## Track 4 — Spider-kin residents and names

**Depends on:** tracks 1–3. **PR:** restricted arrivals, identity preservation, approved names and language text.

Include only explicitly identified underground Stoneborn holds and goblin villages in the spider arrival candidates. Keep the ordinary orc/goblin route intact. Create Beastfolk with the explicit ARACHNID variant, assign the intended home/faction and persist it. Include these arrivals in an appropriate per-settlement cap (propose one initially), with infrequent arrivals and the existing unseen-player safeguard. Count the actual variant, not greenskin membership. Do not rely on `Land.DEEP` alone: that would allow unrelated caves and cannot include goblin factions by itself.

Protect admitted residents from `Havens.settle` recolouring and from later enlistment resetting their variant. Test goblin hostility/targeting so an accepted resident is not immediately attacked as an outsider. Keep the exception tied to the resident and host, rather than admitting every Beastfolk into every Stoneborn haven. Player character choices, existing spider kin, inheritance and free travel are outside the newcomer restriction; Andy can broaden it explicitly.

Draft personal names, not canon: **Neris, Sella, Vesh, Tavil, Orra, Keth**. Optional bynames: **Silkhand, Threadkeeper, Underarch, Quietstep**. Examples: Neris Silkhand, Vesh Underarch, Orra Quietstep. Recommend plain personal names with occasional work/home bynames; avoid treating people as monsters or using the brood queen's titles. Implement only Andy's approved set, through Kingdoms language parts where applicable.

**Checks:** repeated arrivals select only the two approved host kinds; none at surface, human, other-haven or arbitrary dark-cave settlements; cap holds across unload/restart; variant and name survive the haven settling window and recruitment; goblin neighbours tolerate their own resident; bed/work access and shut-in recovery remain below. Verify MCA-unavailable behavior returns safely without partial residents.

## Delivery and verification

Sequence 1 → 2 → 3 → 4; these are small separate PRs, not permission to implement unresolved choices. Each implementation PR includes a Kingdoms change note and CI/Deck build results, with playtests reported separately. This docs-only PR runs `tools/pr_checks.py` and a diff check; it does not run Gradle or claim in-game validation. No version, MODLOG, codemap, Serverfiles or Lore.md changes.

## Open questions for Andy

1. **Where is “down there”: deepslate caves (Y -64 to 0), or under the mountains?** Recommend **under mountains in the Overworld**, in roofed natural caverns, because the Stoneborn's origin and tin workings belong there. Mountain classification must come from the land above, not the cave biome. This is not the Beneath. If you choose all deepslate caves instead, the same floor search works with that band and the proposed lore wording changes. Neither choice is assumed approved.
2. **Stoneborn building choices — tick the ones you want:**
   - [ ] Homes cut into cavern walls, arched doors facing a common hall (recommended).
   - [ ] Freestanding squat stone houses on the cavern floor instead.
   - [ ] Dark local stone with pale stone trim and warm lanterns (recommended).
   - [ ] Mostly rough natural rock, timber supports and miners' fittings instead.
   - [ ] Small shared hall, workshop and mine entrance first; expand gradually (recommended).
   - [ ] Monumental pillared hall as the starting centre instead.
3. Keep goblin mushroom huts in a compact cave layout, or require caverns large enough for their current full stockade and towers?
4. Approve the draft spider-kin names/byname style above, or which should change? Is “settle only here” limited to new arrivals as proposed, or should births/other automatic population routes also be restricted?
5. Approve the three `(new)` lore placements? Should hold food come from trade and existing supplies initially, or should a separate cave-food design be planned? Proposed starting caps are one goblin neighbour per hold and one spider newcomer per settlement; are these suitable for the first playtest?
