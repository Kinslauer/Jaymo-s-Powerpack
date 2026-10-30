package com.jaymoskingdoms;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jaymo's Kingdoms: kingdoms rise at the world's villages, each under a king who is a boss in his own right; players
 * pledge to one, climb its ranks and fill its treasury; and every kingdom keeps its own opinion of every player.
 */
public final class JaymosKingdoms implements ModInitializer {
	public static final String MOD_ID = "jaymoskingdoms";
	public static final Logger LOGGER = LoggerFactory.getLogger("Jaymo's Kingdoms");

	public static EntityType<Folk> FOLK;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id("folk"));
		FOLK = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Folk>of(Folk::new, MobCategory.MISC)
			.sized(0.6F, 1.95F)
			// 0.5.32: sits down into a saddle or seat like a zombie (without it his feet rested on the saddle: he floated)
			.ridingOffset(-0.7F)
			.clientTrackingRange(10)
			.build(key));
		FabricDefaultAttributeRegistry.register(FOLK, Folk.guardAttributes());
		// (0.7.281) first, so a use or hit another listener answers still counts: villagers just dealt with don't teleport
		Attention.register();
		// 0.5.21: the workers' villager professions (with MCA, a settlement's workers are its villagers), MCA told of them
		Professions.register();
		McaCompat.register();
		// (0.7.162) OmniCrossbow's Omni enchantment never turns up: Make Do is the only way to it
		OmniCrossbows.register();
		// (0.7.251) spoken lines: the narrator and the spirits
		Voices.register();
		// the attachments (registered as their classes load)
		LOGGER.debug("kingdom attachments: {} {} {}", Factions.ALL, Factions.KEEPS, Reputation.REPUTATION);
		KingdomEvents.register();
		// barbarian camps, their raiders and raids (and their attachment)
		LOGGER.debug("camp attachment: {}", Camps.CAMPS);
		LOGGER.debug("illager attachment: {}", Illagers.TAG);
		Camps.register();
		// 0.7.148: outlaws spared by the barbarian camps
		Barbarians.register();
		// 0.5.27: goblin villages' masks, and passing for a goblin in one
		Disguise.register();
		// 0.7.250: Blossom villages rising among the cherry trees, and the Blossom land's guards in the ronin harness
		BlossomVillages.register();
		// 0.7.101: the hidden blade (Stealth 5), its leap and the Crippled status
		HiddenBlade.register();
		// (0.7.111) mob senses (light, sneaking, view cone; losing track; the awareness gauge) and unaware blows
		Senses.register();
		// (0.7.155) zombie awareness (Zombie Awareness, remade): zombies hear noise, see lights and smell blood, and call the horde
		Awareness.register();
		// 0.5.29: loot goblins (a port of SkylorBeck's Loot Goblins, LGPL-3.0)
		com.jaymoskingdoms.lootgoblins.LootGoblins.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> KingdomCommands.register(dispatcher));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			KingdomEvents.forget(handler.player);
			McaCompat.forget(handler.player);
		});
		// 0.3, builders and theft: the building catalogue, real-time construction, plots and the plot stake, crime
		LOGGER.debug("builder and theft attachments: {} {} {}", Works.WORKS, Theft.WANTED, Placed.MARKS);
		Plots.register();
		// (batch 4) building: dirt, grass and path slabs; wind chimes; the rope lift (rope, anchor, pulley, platform)
		EarthSlabs.register();
		WindChimes.register();
		RopeLift.register();
		// (0.7.116) the surveyor's rod, players' own designs and work areas
		Surveyor.register();
		Designs.register();
		Armory.register();
		// knitted wool clothes: light armour that keeps the cold off an Emberborn
		WoolClothes.register();
		Buildings.register();
		Construction.register();
		// 0.7.202: the world weathers (moss, cracks, mud, leaf litter, tufts); a settlement's builders keep up its upkeep
		Upkeep.register();
		Weathering.register();
		// 0.5.58: a builder of every crew levels the settlement's ground (pits, ledges, gaps) before anything else
		Groundwork.register();
		// 0.5.11: lost buildings put up again; the settlement's people answer fires
		Rebuilds.register();
		Firefighting.register();
		// 0.7.31: disasters strike settlements: news, repairs, tempers, relief quests
		DisasterRelief.register();
		// 0.5.23: guards burn monsters' corpses (with Jaymo's Kitchen), or throw them into a fire pit or lava
		Undertakers.register();
		// 0.7.132: townsfolk bury their dead in a graveyard (burn the enemy's), and mourn; looters and the poor rob the dead
		LOGGER.debug("grave attachment: {}", Bearers.GRAVES);
		Bearers.register();
		Looters.register();
		DevCommands.register();
		// 0.5.2: the mine the builders dig first (it wraps their brain; Debris wraps it in turn) and its attachment
		LOGGER.debug("mine attachment: {}", Mines.MINES);
		Mines.register();
		// 0.5.4: living trees (saplings grow up by stages, trees fell whole, the woods seed themselves) and their attachment
		LOGGER.debug("growing tree attachment: {}", Trees.GROWING);
		// 0.5.9: felled trees topple over whole (and their attachments)
		FallingTree.register();
		LOGGER.debug("falling tree attachments: {} {}", FallingTree.PIECES, FallingTree.HEADING);
		LOGGER.debug("fallen trunk attachment: {}", FallenTrunks.TRUNKS);
		// 0.5.25: trees take chops to fell (TreeChop's way), the chopped logs narrowing
		ChoppedLog.register();
		Trees.register();
		FallenTrunks.register();
		// 0.5.5: the working folk sleep in beds at night; villagers take a trade at the settlement's job sites and work it
		Homes.register();
		Jobs.register();
		// 0.7.220: players' jobs (orders, wages, ranks and perks)
		Careers.register();
		// 0.5.12: javelins for guards; rulers' tempers
		Javelins.register();
		RangedTactics.register();
		Tempers.register();
		// (0.7.184) before Retinue, Pickpocket and Quests: sneak-using your own child with an earned blood waiting
		Lineage.register();
		Elders.register();
		Retinue.register();
		Audiences.register();
		Doorways.register();
		// (0.7.138) with MCA, rulers are the town's own villagers
		LOGGER.debug("reign attachment: {}", Rulers.REIGNS);
		// (0.7.175) a ruler's or guard's standing, shown on MCA's screen (synced: registered on both sides)
		LOGGER.debug("standing attachment: {}", Standing.LINES);
		Rulers.register();
		// 0.7.242: ship crews as MCA villagers
		Crewmen.register();
		// (0.7.292) goblins and orcs as MCA villagers
		CampChiefs.register();
		StoryTalk.register();
		ShipTalk.register();
		ZFighterFolk.register();
		// 0.7.130: the purist ruler's townsfolk mind a stranger's blood
		Purists.register();
		Prejudice.register();
		Havens.register();
		Emigrants.register();
		// 0.7.299: meals cooked at home or bought at a cookhouse or tavern, and visitors for the day
		Meals.register();
		Visitors.register();
		LOGGER.debug("visitor attachment: {}", Visitors.VISIT);
		Reforms.register();
		LOGGER.debug("reform attachment: {}", Reforms.GRIEVANCES);
		LOGGER.debug("emigrant attachments: {} {} {} {}", Emigrants.MOVE, Emigrants.STORY, Emigrants.ROADS, Emigrants.EMPTIED);
		LOGGER.debug("haven attachment: {}", Havens.HAVENS);
		Frights.register();
		Clearing.register();
		Herds.register();
		// 0.7.84: villagers shut in somewhere with no way out turn up on open ground
		ShutIn.register();
		// 0.6.4: Jaymo's Powers' aliens (the Xeno-Band) are monsters to folk who haven't seen you change (and its attachment)
		LOGGER.debug("alien attachment: {}", Aliens.KNOWN);
		LOGGER.debug("tail attachment: {}", Tails.EXPLAINED);
		Aliens.register();
		// 0.7.62: Jaymo's Powers' costumes: a masked player is a stranger, the costume someone of its own (and its attachments)
		LOGGER.debug("identity attachments: {} {} {}", Identities.ALL, Identities.SIGHTINGS, Identities.MASKED_AS);
		Identities.register();
		// 0.7.149: the name picked in MCA's creator shows in place of the username (and its attachment)
		CharacterNames.register();
		// 0.7.180: names over heads only once you've been told them (and its attachments)
		Introductions.register();
		// (0.7.184) handshakes between players, and the groups they make
		Groups.register();
		Handshakes.register();
		// (0.7.264, Andy's NOEA picks) parties: invites, no friendly fire, shared slay quests, members' stats (and its attachment)
		LOGGER.debug("party attachment: {}", Parties.META);
		Parties.register();
		Oddities.register();
		// 0.5.21: monsters hunt the settlements' folk as they do villagers
		Prey.register();
		// 0.7.56: monsters see farther (Farsighted Mobs) and creepers stalk you (Stalker Creepers), both remade
		Farsight.register();
		Stalkers.register();
		// 0.7.103: baby zombies weaker, endermen stand by each other (and teleport with thunder)
		MobTweaks.register();
		VillagerHunters.register();
		Theft.register();
		Retaliation.register();
		// 0.7.73: guards are downed, not killed: they crawl or lie there, can be disarmed, never looted (before
		// Pickpocket and the villager screens, whose right-click on them it takes); 0.7.130: the downed state itself is
		// Jaymo's Powers' now (read by its scoreboard tag), this keeps the tending and the disarming
		Downed.register();
		// 0.7.102: clerics are barber-surgeons, taverns and festivals sell drink (Powers' and Kitchen's wares by id, before
		// the villager screens open, so the trades are there when they do)
		Remedies.register();
		// (0.7.383) what is beyond a player's world tier isn't offered to them (before the smiths grade what is left)
		TierGate.register();
		// a realm's smiths sell better-forged gear to those it thinks well of (with Jaymo's Powers)
		GradedTrades.register();
		// 0.7.85: a disarmed guard draws a sidearm or goes for a weapon on the ground
		Rearm.register();
		// 0.5.50: pickpocketing (before Quests, whose sneak-use on a villager it takes from behind)
		Pickpocket.register();
		// 0.7.234: locked loot chests and the Pin and Rotary lockpicking games (and its attachment)
		LOGGER.debug("lock attachment: {}", Lockpicking.LOCK);
		Lockpicking.register();
		// 0.7.126: crooks among the villagers (theft, muggings, arson, pickpockets, scams) and their attachments
		LOGGER.debug("crook attachments: {} {} {}", Crooks.CROOK, Crooks.NOTORIETY, Crooks.HOME);
		Crooks.register();
		// 0.7.126: the guards arrest crooks and march them to the ruler for judgment
		Arrests.register();
		// 0.7.211: the underworld: Pmeh and Shash bricks, speakeasies, dice, busts and the cells (their attachments)
		LOGGER.debug("underworld attachments: {} {} {} {} {}", Underworld.DENS, Underworld.FOUND, Busts.OFFENCES, Busts.CHARGES, Busts.JAILED);
		Underworld.register();
		// 0.7.224: the den's bookie (wars and pit fights) and its fight pit (and its attachment)
		LOGGER.debug("fight pit attachments: {} {} {}", FightPit.PITS, FightPit.RECORD, FightPit.HELD);
		Bookies.register();
		FightPit.register();
		// 0.5.52: the skill tree (Survival, Stealth, Strength), alignment and renown, and their attachments
		Skills.register();
		// (0.7.353) Might and Packhorse ease hauling bodies in Kitchen
		KitchenCompat.registerBrawn();
		LOGGER.debug("skill attachments: {} {} {} {}", Skills.PROGRESS, SurvivalSkill.LAST_STAND, Renown.TITLE, Renown.KNOWN);
		LOGGER.debug("will attachments: {} {}", Will.ROAR_READY, Will.REFUSE_READY);
		LOGGER.debug("valor attachments: {} {} {} {} {} {}", Valor.VALOR, Valor.STATE, ValorFolk.SEEN, ValorFolk.TOLD, ValorFolk.LED, ValorFolk.AWED);
		// 0.6.0: player classes (picked on first joining; a Writ of Rebirth unmakes the pick) and their attachments
		Classes.register();
		LOGGER.debug("class attachments: {} {}", Classes.CLASS, Classes.WRIT_BOUGHT);
		// 0.7.57: heritage (Origins remade: picked between MCA's destiny and the class) and its attachment
		Heritage.register();
		// 0.7.82: world tiers (after Apotheosis's): each player's chosen difficulty, foes near them take it (its attachments)
		LOGGER.debug("world tier attachments: {} {} {} {} {}", WorldTiers.TIER, WorldTiers.PROGRESS, WorldTiers.CHANGED, WorldTiers.MOB_TIER,
			WorldTiers.FLOOR);
		WorldTiers.register();
		// (0.7.386) star-touched foes on Jaymo's Powers' far worlds (its attachment)
		LOGGER.debug("star-touched attachment: {}", StarTouched.STAR);
		StarTouched.register();
				// (0.7.381) the ground changes under each rung, and the Rung Journal hints at them (their attachments)
		GroundChanges.register();
		com.jaymoskingdoms.journal.RungJournal.register();
		FrostPrison.register();
		LOGGER.debug("heritage attachments: {} {}", Heritage.HERITAGE, FrostPrison.LAST);
		// (0.7.262, Andy's NOEA picks) the Divine-born, the Bio-built (its forms), the Rubber-folk and the held heritage key
		LOGGER.debug("bio-built and rubber-folk attachments: {} {} {} {} {} {} {} {} {}", BioBuilt.FORM, BioBuilt.WORTH, HeritageHold.ABSORBING, RubberFolk.ADOPTED,
			RubberStomach.STOMACH, RubberStomach.MASS, RubberStomach.INSIDE, RubberStomach.HOLDING, RubberStomach.SAFE);
		HeritageHold.register();
		DivineBorn.register();
		BioBuilt.register();
		RubberFolk.register();
		RubberStomach.register();
		// 0.7.214: heritage variants, three looks to every heritage (and its attachment)
		LOGGER.debug("heritage variant attachment: {}", HeritageVariants.VARIANT);
		HeritageVariants.register();
		// 0.7.216: the life story, a newcomer's heritage, class, power and start told as their past (and its attachment)
		LOGGER.debug("life story attachment: {}", LifeStory.STORY);
		LifeStory.register();
		// 0.7.381: the village start's village takes you in, whatever your blood (and its attachments)
		LOGGER.debug("story home attachments: {} {}", StoryHomes.HOME, StoryHomes.PENDING);
		StoryHomes.register();
		StoryGround.register();
		// 0.5.19: a boss's bar shows only to the players who've hit it
		BossBars.register();
		// 0.3, war: declarations, war parties, banners, peace (and its attachment)
		LOGGER.debug("war attachment: {}", WarData.WARS);
		LOGGER.debug("great wars attachment: {}", GreatWars.LEDGER);
		Wars.register();
		// 0.7.65: a realm at war is on alert: bells, sandbags round the seat, civilians hiding from a war party (and its attachment)
		Sandbags.register();
		// carpentry (after Better With Mods): timber trim, benches and tables, wattle panels
		com.jaymoskingdoms.carpentry.Carpentry.register();
		LOGGER.debug("war alert attachment: {}", WarAlert.RAISED);
		WarAlert.register();
		// 0.4, combat: Better Combat's weapon moves for humanoid mobs, and folk's sidestep (only with Better Combat)
		MobCombat.register();
		// (0.7.105) Just-In COMBAT's ideas, our own way: shields raised, blades that clash
		MobShields.register();
		MobParry.register();
		BladeClash.register();
		Speech.register();
		// 0.7.332: proximity chat (typed chat heard only nearby) and voice-changer masks (payload before any receiver)
		ProximityChat.register();
		VoiceMasks.register();
		// 0.4.1: ATi Structures' above-ground buildings in compounds (only with ATi loaded)
		AtiCompounds.register();
		// 0.5: seats in the world's buildings (and their attachment); fiefdoms and camps in Grim Kingdoms' ruins (only with
		// Grim loaded), their builders clearing its rubble first (after Construction, whose brain it wraps)
		LOGGER.debug("hold attachment: {}", Holds.HOLDS);
		GrimSites.register();
		// 0.5.1: a night slept through made up at once (building, fields), farmers, rulers' days out
		LOGGER.debug("warden attachment: {}", Wardens.WARDENS);
		// (0.7.225) players on the throne: usurpers, heirs by marriage, conquerors, founders (and their attachment)
		LOGGER.debug("crown attachment: {}", Crowns.CROWNS);
		Crowns.register();
		Nights.register();
		// (0.7.5) the night as a timelapse, and resting in bed awake (our own take on Sleeping Overhaul 2)
		Timelapse.register();
		BedRest.register();
		// (0.7.17) placed torches burn down and go out (our own take on Realistic Torches)
		Torches.register();
		// (0.7.17) spiders take after their biome (our own take on Spider Overhaul)
		com.jaymoskingdoms.wild.SpiderKinds.register();
		// (0.7.97) every spider small (More Babies' sizes) and hunting with Yuka-style steering on Stormie's climbing
		com.jaymoskingdoms.wild.SpiderMinds.register();
		// 0.5.14: horses and wagons: mounted guards and archers for prosperous realms, a wealthy king's carriage, and the
		// fiefdoms' caravans to their kingdoms (and their attachment)
		Wagon.register();
		// 0.5.64: the richest kingdoms' cannons (their musketeers are Muskets, ticked by each guard)
		Cannon.register();
		ClockworkGolem.register();
		// (0.7.351) the Skeleton Lord, asleep in some of the rogue golems' lairs
		SkeletonLord.register();
		// (0.7.339) friendly war barrels, built through Golemancy (the goblin villages' own are hired by WarBarrel.hire)
		WarBarrel.register();
		// (0.7.333) rogue clockwork golems as dungeon mini-bosses (and their attachment)
		GolemLairs.register();
		Cannonball.register();
		// (0.7.107) the pirate bombardiers' dynamite (the thrown stick and the item)
		Dynamite.register();
		Cannons.register();
		LOGGER.debug("caravan attachments: {} {}", Caravans.CARAVANS, Carriages.BOUGHT);
		Caravans.register();
		// (0.7.13) roaming pillager and merchant caravans (our own take on Pillager Caravans) and their attachment
		LOGGER.debug("roaming caravan attachment: {}", RoamingCaravans.LEDGER);
		RoamingCaravans.register();
		// (0.7.9) King's Roads between settlements, kept to by cabs, rickshaws, rulers and caravans
		LOGGER.debug("road attachment: {}", Roads.ROADS);
		Roads.register();
		// 0.7.103: roads are quicker going, sand, snow and leaves slower
		RoadPace.register();
		LOGGER.debug("charted road attachment: {}", Milestones.CHARTED);
		Milestones.register();
		Cavalry.register();
		// 0.5.34: Jaymo's Equestrian, stage 1 (breeds, stats, gaits, stamina, courage, coats, war elephants) and its attachments
		com.jaymoskingdoms.equestrian.Equestrian.register();
		com.jaymoskingdoms.equestrian.MythicMounts.register();
		// 0.5.44: Jaymo's Equestrian, stage 2 (bond and tack: trust, disciplines, tricks, needs, tack) and its attachments
		com.jaymoskingdoms.equestrian.Bonding.register();
		// 0.7.305: Jaymo's Equestrian, stage 3 (the herd: mares and stallions, orders, defending, the roster; riding extras; breed gifts)
		com.jaymoskingdoms.equestrian.Herding.register();
		com.jaymoskingdoms.equestrian.Mounted.register();
		com.jaymoskingdoms.equestrian.BreedGifts.register();
		// (0.5.47) stables at work: boarding, the horse market, the farrier
		Stables.register();
		Carriages.register();
		Rickshaws.register();
		Cabs.register();
		// (0.7.266) players' rides go by on the road screen
		Journeys.register();
		// 0.5.29: quests from villagers and kings, their caches and champions, relics (and their attachments)
		LOGGER.debug("quest attachments: {} {} {} {}", Quests.LOG, Quests.OFFER, Quests.MARK, QuestFoes.FOE);
		// 0.5.38: where newcomers and immigrants are from (skin tone by climate)
		LOGGER.debug("origin attachment: {}", SkinTones.ORIGIN);
		// 0.7.23: the Verdant World's villagers are Verdants (green skin, antennae; and its attachment)
		LOGGER.debug("verdant attachment: {}", Verdants.SHADE);
		Verdants.register();
		// villagers born into heritages (MCA's and the settlements' own folk), a Warrior-born's tail wrapped or cut
		VillagerHeritage.register();
		Quests.register();
		// 0.5.50: the traveller's journal (book + map): quests, a map, reputations, on M
		Journal.register();
		// 0.7.55: tailors change your clothes for a fee (MCA's editor, clothing page only)
		Tailors.register();
		// 0.5.39: the townsfolk come over to you: questions, quests, requests, bargains, news
		Approaches.register();
		// 0.7.61: villagers react to your deeds (fires put out, lives saved, captives brought home)
		Deeds.register();
		Captives.register();
		// 0.7.109: each villager remembers each player (gifts, trades, crimes, rescues...), and the chat AI is told (and its attachment)
		LOGGER.debug("memory attachment: {}", Recollections.MEMORIES);
		Recollections.register();
		SharedHistory.register();
		Rescues.register();
		// 0.7.186: every humanoid's own temperament (and its attachment)
		LOGGER.debug("temperament attachment: {}", Temperaments.TEMPERAMENT);
		Temperaments.register();
		// 0.7.218: villagers who awaken to a power (Jaymo's Powers fights with it)
		GiftedFolk.register();
		GiftTales.register();
		// 0.7.20: tongues by region; only simple dealings with folk whose tongue you don't speak (and its attachments)
		LOGGER.debug("tongue attachments: {} {}", Languages.HOME, Languages.LEARNED);
		Languages.register();
		// (0.6.9) tag, little ones, daily routines, suspicion in their homes
		Tag.register();
		Families.register();
		Routines.register();
		// 0.7.19: villagers' regard for one another, and the meetings it makes (fights to hugs) (and its attachment)
		LOGGER.debug("regard attachment: {}", Neighbours.REGARD);
		Neighbours.register();
		// 0.7.21: grudges: villagers who hate another set you on them (a beating, or a lure into an ambush)
		Grudges.register();
		Trespass.register();
		// 0.7.46: after Village Life: festival days, village cats and dogs, couriers, hunger and weariness (their attachments)
		LOGGER.debug("village life attachments: {} {}", Festivals.CALLED, Pets.VILLAGE_PET);
		Festivals.register();
		Pets.register();
		// 0.7.201: letters, messenger pigeons and couriers across the land; the news by letter, not chat (and their attachments)
		LOGGER.debug("post attachments: {} {} {} {}", Couriers.ERRANDS, PigeonPost.FLIGHTS, Post.NAMES, Post.WAITING);
		Letters.register();
		Pigeon.register();
		PigeonPost.register();
		Post.register();
		Couriers.register();
		Spirits.register();
		// 0.5.37: sorcery: camps' shamans and necromancers, courts' wizards, the risen dead, spell scrolls (and the thralls' attachment)
		Arcana.register();
		// the elemental spell tomes: left-click bolts, a loaded page on right click
		Tomes.register();
		LOGGER.debug("thrall attachment: {}", Thralls.TAG);
		Thralls.register();
		Sorcery.register();
		// 0.5.62: the villager on a floating island is a hermit wizard with a story
		Hermits.register();
		// 0.7.384: star charts for sale, and lights in the sky in the Tidings
		StarFolk.register();
		Starfall.register();
		// Naturalist's predators as rare wilderness encounters (0.5.55)
		com.jaymoskingdoms.wild.Wild.register();
		// monsters of legend after Fangs 'n Claws' list (0.5.56)
		com.jaymoskingdoms.monsters.Monsters.register();
		// (0.7.329) the second batch of Blockbench Workshop creatures
		com.jaymoskingdoms.beasts.Beasts.register();
		// 0.7.137: the wind dragons (Notus first)
		com.jaymoskingdoms.dragons.Dragons.register();
		// 0.7.145: the Aether's remnants: monoliths and their four Giants, the first Giant's fall raising the world tier
		com.jaymoskingdoms.remnants.Monoliths.register();
		Encounters.register();
		Sightings.register();
		// 0.5.59: assassins: contracts on rulers and marked players, whispers, the attempt (and the contracts' attachment)
		LOGGER.debug("contract attachment: {}", Assassins.CONTRACTS);
		Assassins.register();
		// 0.6.6: lunar events (blood, harvest, blue moons) and Bad Omen grudges (their attachments)
		LOGGER.debug("moon and grudge attachments: {} {}", Moons.ROLL, Omens.GRUDGES);
		Moons.register();
		Omens.register();
		// 0.6.12: natural disasters, stage 1: storm fronts, tornadoes, the storm bell, weathervane and barometer
		com.jaymoskingdoms.disasters.Disasters.register();
		// 0.7.103: sinkholes: sand and gravel over nothing gives way under a player
		com.jaymoskingdoms.disasters.LooseGround.register();
		// 0.7.0: vampires: the Elder, the bite and the turned (their attachments)
		LOGGER.debug("vampire attachments: {} {} {}", Vampires.VAMPIRE, Vampires.BITTEN, Vampires.ELDER);
		Vampires.register();
		// 0.7.0: lycanthropy: the cursed change under the full moon (their attachments)
		LOGGER.debug("lycan attachments: {} {}", Lycans.CURSE, Lycans.FORCED);
		Lycans.register();
		// 0.7.0: players as vampires (by a bite) and werewolves (by ritual)
		VampirePlayers.register();
		WerewolfPlayers.register();
		// 0.7.66: the night's gifts for vampire and werewolf players (dash, groundpound, leap, howl, kin-sense, wings...;
		// their attachments)
		LOGGER.debug("night gift attachments: {} {}", NightGifts.GIFT, VampirePlayers.HALF_TURN);
		NightGifts.register();
		// 0.7.88: Nightborn and vampires (the cure's trace, an attachment)
		LOGGER.debug("nightborn blood attachment: {}", NightbornBlood.TRACE);
		NightbornBlood.register();
		// 0.7.332: deception and temptation: favourite scent, whispers, thralls, sleeping prey, the Mask, tells, garlic and silver,
		// the blood's requests (their attachments)
		LOGGER.debug("vampire deception attachments: {} {} {} {} {} {} {} {} {} {}", Favourites.TIME, Favourites.FAVOURITE, Favourites.SEEN,
			BloodThralls.NIGHTS, BloodThralls.MASTER, BloodThralls.BEAT, SleepingPrey.BITE_MARKS, VampireMask.MASK, BloodRequests.STATE, Garlic.class);
		BloodTemptation.register();
		BloodThralls.register();
		SleepingPrey.register();
		VampireMask.register();
		VampireTells.register();
		Garlic.register();
		// 0.7.185: losing yourself to the blood or the curse: the blood fever's urges, the wolf's hunts, and the holds that
		// drive you (their attachments)
		LOGGER.debug("takeover attachments: {} {} {} {} {} {} {}", BloodFever.FEVER, BloodFever.SURRENDER, BloodFever.HEARTBEAT, FeralCurse.FERAL,
			MoonPull.HEARTBEAT, HiddenChange.TELLS, CurseRequests.ASK);
		Seizures.register();
		BloodFever.register();
		FeralCurse.register();
		// 0.7.332: the werewolf's deception: the moon's pull among friends, scent trails, the tells of a hidden change, a locked
		// room, the morning after, torn cloth as evidence, and what the curse asks for
		MoonPull.register();
		ScentTrails.register();
		HiddenChange.register();
		Caging.register();
		MorningAfter.register();
		TornCloth.register();
		ClothesColour.register();
		CurseRequests.register();
		// 0.7.332: paranoia and deception: rumours with names, accusing at the priest, victim memory, monster kinship (their attachments)
		LOGGER.debug("paranoia attachments: {} {} {} {}", Suspicion.MARKS, Accusations.DEAF_UNTIL, VictimMemory.BITER, Kinship.HEARD);
		Suspicion.register();
		Accusations.register();
		VictimMemory.register();
		Kinship.register();
		// 0.7.129: the Nightfall Plague (our own after the Forge mod): the plague and its cure, Night Bleeders, vampire bats,
		// the vampires' callings, nightmares and the Nightfall (and their attachments)
		com.jaymoskingdoms.nightfall.Nightfall.register();
		// 0.7.131: the Nether's faction, stage 1: the Hornkin and their gold feud with the piglins (our own after Just-In NETHER)
		com.jaymoskingdoms.nether.Nether.register();
		// 0.7.92: an Earthling near death: the ki round the fists flares, with a buff (its attachment)
		LOGGER.debug("earthling surge attachment: {}", EarthlingSurge.UNTIL);
		EarthlingSurge.register();
		// 0.7.95: a Warrior-born back from the brink: an attribute point (its attachments)
		LOGGER.debug("warrior zenkai attachments: {} {}", WarriorZenkai.POINTS, WarriorZenkai.NEXT);
		WarriorZenkai.register();
		// 0.7.7: sailing, stage 1: ships built from blocks (the helm, sails, the ship and its attachment)
		com.jaymoskingdoms.sailing.Sailing.register();
		// (0.7.206) whaling: great whales, roped harpoons, carcasses to tow and flense
		com.jaymoskingdoms.whaling.Whaling.register();
		// 0.7.42: sailing, stage 3: ships with crews of their own (pirates, warships, merchantmen), pirate coves, harbours,
		// treasure wrecks (and their attachments)
		Corsairs.register();
		Navies.register();
		Whalers.register();
		TreasureWrecks.register();
		LOGGER.debug("sailing crew attachments: {} {} {} {} {}", com.jaymoskingdoms.sailing.Ship.SEAT, PirateCoves.COVES, Navies.HARBOURS,
			TreasureWrecks.CHARTED, TreasureWrecks.LAID);
		LOGGER.debug("ship attachment: {}", com.jaymoskingdoms.sailing.Ship.PIECES);
		// 0.7.126: bandit masks and bandanas; the bounty board, bounties on crooks and players (their attachments)
		Bandits.register();
		// 0.7.171: rulers' crowns
		Regalia.register();
		RoyalDress.register();
		LOGGER.debug("bounty attachments: {} {} {} {}", Bounties.ALL, Bounties.BOARDS, Bounties.BOARDS_AT, Bounties.ARMED);
		Bounties.register();
		MerchantOrders.register();
		// BWM batch 5: the gloom of the dark (and its attachment)
		Gloom.register();
		LOGGER.debug("gloom attachment: {}", Gloom.GLOOM);
		// 0.7.326: villagers drawn as a Blockbench body of their own (the warchief)
		LOGGER.debug("body attachment: {}", Bodies.BODY);
		// BWM batch 5: iron and emerald beacons keep watch (and its attachment)
		BeaconWatch.register();
		// (0.7.289) a villager's first meeting with you is a handshake (last, so every other use of a villager goes first)
		Handshakes.registerGreetings();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Farmers.clear();
			Gatherers.clear();
			Royals.clear();
			Wardens.clear();
			Crowns.clear();
			Focus.clear();
		});
	}
}
