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
		// 0.5.21: the workers' villager professions (with MCA, a settlement's workers are its villagers), MCA told of them
		Professions.register();
		McaCompat.register();
		// the attachments (registered as their classes load)
		LOGGER.debug("kingdom attachments: {} {} {}", Factions.ALL, Factions.KEEPS, Reputation.REPUTATION);
		KingdomEvents.register();
		// barbarian camps, their raiders and raids (and their attachment)
		LOGGER.debug("camp attachment: {}", Camps.CAMPS);
		LOGGER.debug("illager attachment: {}", Illagers.TAG);
		Camps.register();
		// 0.5.27: goblin villages' masks, and passing for a goblin in one
		Disguise.register();
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
		Armory.register();
		Buildings.register();
		Construction.register();
		// 0.5.58: a builder of every crew levels the settlement's ground (pits, ledges, gaps) before anything else
		Groundwork.register();
		// 0.5.11: lost buildings put up again; the settlement's people answer fires
		Rebuilds.register();
		Firefighting.register();
		// 0.7.31: disasters strike settlements: news, repairs, tempers, relief quests
		DisasterRelief.register();
		// 0.5.23: guards burn monsters' corpses (with Jaymo's Kitchen), or throw them into a fire pit or lava
		Undertakers.register();
		DevCommands.register();
		// 0.5.2: the mine the builders dig first (it wraps their brain; Debris wraps it in turn) and its attachment
		LOGGER.debug("mine attachment: {}", Mines.MINES);
		Mines.register();
		// 0.5.4: living trees (saplings grow up by stages, trees fell whole, the woods seed themselves) and their attachment
		LOGGER.debug("growing tree attachment: {}", Trees.GROWING);
		// 0.5.9: felled trees topple over whole (and their attachments)
		FallingTree.register();
		LOGGER.debug("falling tree attachments: {} {}", FallingTree.PIECES, FallingTree.HEADING);
		// 0.5.25: trees take chops to fell (TreeChop's way), the chopped logs narrowing
		ChoppedLog.register();
		Trees.register();
		// 0.5.5: the working folk sleep in beds at night; villagers take a trade at the settlement's job sites and work it
		Homes.register();
		Jobs.register();
		// 0.5.12: javelins for guards; rulers' tempers
		Javelins.register();
		Tempers.register();
		Retinue.register();
		Frights.register();
		// 0.6.4: Jaymo's Powers' aliens (the Xeno-Band) are monsters to folk who haven't seen you change (and its attachment)
		LOGGER.debug("alien attachment: {}", Aliens.KNOWN);
		LOGGER.debug("tail attachment: {}", Tails.EXPLAINED);
		Aliens.register();
		Oddities.register();
		// 0.5.21: monsters hunt the settlements' folk as they do villagers
		Prey.register();
		Theft.register();
		// 0.5.50: pickpocketing (before Quests, whose sneak-use on a villager it takes from behind)
		Pickpocket.register();
		// 0.5.52: the skill tree (Survival, Stealth, Strength), alignment and renown, and their attachments
		Skills.register();
		LOGGER.debug("skill attachments: {} {} {} {}", Skills.PROGRESS, SurvivalSkill.LAST_STAND, Renown.TITLE, Renown.KNOWN);
		// 0.6.0: player classes (picked on first joining; a Writ of Rebirth unmakes the pick) and their attachments
		Classes.register();
		LOGGER.debug("class attachments: {} {}", Classes.CLASS, Classes.WRIT_BOUGHT);
		// 0.5.19: a boss's bar shows only to the players who've hit it
		BossBars.register();
		// 0.3, war: declarations, war parties, banners, peace (and its attachment)
		LOGGER.debug("war attachment: {}", WarData.WARS);
		Wars.register();
		// 0.4, combat: Better Combat's weapon moves for humanoid mobs, and folk's sidestep (only with Better Combat)
		MobCombat.register();
		Speech.register();
		// 0.4.1: ATi Structures' above-ground buildings in compounds (only with ATi loaded)
		AtiCompounds.register();
		// 0.5: seats in the world's buildings (and their attachment); fiefdoms and camps in Grim Kingdoms' ruins (only with
		// Grim loaded), their builders clearing its rubble first (after Construction, whose brain it wraps)
		LOGGER.debug("hold attachment: {}", Holds.HOLDS);
		GrimSites.register();
		// 0.5.1: a night slept through made up at once (building, fields), farmers, rulers' days out
		LOGGER.debug("warden attachment: {}", Wardens.WARDENS);
		Nights.register();
		// (0.7.5) the night as a timelapse, and resting in bed awake (our own take on Sleeping Overhaul 2)
		Timelapse.register();
		BedRest.register();
		// (0.7.17) placed torches burn down and go out (our own take on Realistic Torches)
		Torches.register();
		// (0.7.17) spiders take after their biome (our own take on Spider Overhaul)
		com.jaymoskingdoms.wild.SpiderKinds.register();
		// 0.5.14: horses and wagons: mounted guards and archers for prosperous realms, a wealthy king's carriage, and the
		// fiefdoms' caravans to their kingdoms (and their attachment)
		Wagon.register();
		// 0.5.64: the richest kingdoms' cannons (their musketeers are Muskets, ticked by each guard)
		Cannon.register();
		Cannonball.register();
		Cannons.register();
		LOGGER.debug("caravan attachments: {} {}", Caravans.CARAVANS, Carriages.BOUGHT);
		Caravans.register();
		// (0.7.13) roaming pillager and merchant caravans (our own take on Pillager Caravans) and their attachment
		LOGGER.debug("roaming caravan attachment: {}", RoamingCaravans.LEDGER);
		RoamingCaravans.register();
		// (0.7.9) King's Roads between settlements, kept to by cabs, rickshaws, rulers and caravans
		LOGGER.debug("road attachment: {}", Roads.ROADS);
		Roads.register();
		LOGGER.debug("charted road attachment: {}", Milestones.CHARTED);
		Milestones.register();
		Cavalry.register();
		// 0.5.34: Jaymo's Equestrian, stage 1 (breeds, stats, gaits, stamina, courage, coats, war elephants) and its attachments
		com.jaymoskingdoms.equestrian.Equestrian.register();
		com.jaymoskingdoms.equestrian.MythicMounts.register();
		// 0.5.44: Jaymo's Equestrian, stage 2 (bond and tack: trust, disciplines, tricks, needs, tack) and its attachments
		com.jaymoskingdoms.equestrian.Bonding.register();
		// (0.5.47) stables at work: boarding, the horse market, the farrier
		Stables.register();
		Carriages.register();
		Rickshaws.register();
		Cabs.register();
		// 0.5.29: quests from villagers and kings, their caches and champions, relics (and their attachments)
		LOGGER.debug("quest attachments: {} {} {} {}", Quests.LOG, Quests.OFFER, Quests.MARK, QuestFoes.FOE);
		// 0.5.38: where newcomers and immigrants are from (skin tone by climate)
		LOGGER.debug("origin attachment: {}", SkinTones.ORIGIN);
		// 0.7.23: the Verdant World's villagers are Verdants (green skin, antennae; and its attachment)
		LOGGER.debug("verdant attachment: {}", Verdants.SHADE);
		Verdants.register();
		Quests.register();
		// 0.5.50: the traveller's journal (book + map): quests, a map, reputations, on M
		Journal.register();
		// 0.7.53: tailors change your clothes for a fee (MCA's editor, clothing page only)
		Tailors.register();
		// 0.5.39: the townsfolk come over to you: questions, quests, requests, bargains, news
		Approaches.register();
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
		Couriers.register();
		Spirits.register();
		// 0.5.37: sorcery: camps' shamans and necromancers, courts' wizards, the risen dead, spell scrolls (and the thralls' attachment)
		Arcana.register();
		LOGGER.debug("thrall attachment: {}", Thralls.TAG);
		Thralls.register();
		Sorcery.register();
		// 0.5.62: the villager on a floating island is a hermit wizard with a story
		Hermits.register();
		// Naturalist's predators as rare wilderness encounters (0.5.55)
		com.jaymoskingdoms.wild.Wild.register();
		// monsters of legend after Fangs 'n Claws' list (0.5.56)
		com.jaymoskingdoms.monsters.Monsters.register();
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
		// 0.7.0: vampires: the Elder, the bite and the turned (their attachments)
		LOGGER.debug("vampire attachments: {} {} {}", Vampires.VAMPIRE, Vampires.BITTEN, Vampires.ELDER);
		Vampires.register();
		// 0.7.0: lycanthropy: the cursed change under the full moon (their attachments)
		LOGGER.debug("lycan attachments: {} {}", Lycans.CURSE, Lycans.FORCED);
		Lycans.register();
		// 0.7.0: players as vampires (by a bite) and werewolves (by ritual)
		VampirePlayers.register();
		WerewolfPlayers.register();
		// 0.7.7: sailing, stage 1: ships built from blocks (the helm, sails, the ship and its attachment)
		com.jaymoskingdoms.sailing.Sailing.register();
		// 0.7.42: sailing, stage 3: ships with crews of their own (pirates, warships, merchantmen), pirate coves, harbours,
		// treasure wrecks (and their attachments)
		Corsairs.register();
		Navies.register();
		TreasureWrecks.register();
		LOGGER.debug("sailing crew attachments: {} {} {} {} {}", com.jaymoskingdoms.sailing.Ship.SEAT, PirateCoves.COVES, Navies.HARBOURS,
			TreasureWrecks.CHARTED, TreasureWrecks.LAID);
		LOGGER.debug("ship attachment: {}", com.jaymoskingdoms.sailing.Ship.PIECES);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Farmers.clear();
			Gatherers.clear();
			Royals.clear();
			Wardens.clear();
			Focus.clear();
		});
	}
}
