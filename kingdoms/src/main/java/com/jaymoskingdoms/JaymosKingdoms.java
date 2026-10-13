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
		LOGGER.debug("builder and theft attachments: {} {}", Works.WORKS, Theft.WANTED);
		Plots.register();
		Armory.register();
		Buildings.register();
		Construction.register();
		// 0.5.11: lost buildings put up again; the settlement's people answer fires
		Rebuilds.register();
		Firefighting.register();
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
		Frights.register();
		// 0.5.21: monsters hunt the settlements' folk as they do villagers
		Prey.register();
		Theft.register();
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
		// 0.5.14: horses and wagons: mounted guards and archers for prosperous realms, a wealthy king's carriage, and the
		// fiefdoms' caravans to their kingdoms (and their attachment)
		Wagon.register();
		LOGGER.debug("caravan attachments: {} {}", Caravans.CARAVANS, Carriages.BOUGHT);
		Caravans.register();
		Cavalry.register();
		// 0.5.34: Jaymo's Equestrian, stage 1 (breeds, stats, gaits, stamina, courage, coats, war elephants) and its attachments
		com.jaymoskingdoms.equestrian.Equestrian.register();
		// 0.5.44: Jaymo's Equestrian, stage 2 (bond and tack: trust, disciplines, tricks, needs, tack) and its attachments
		com.jaymoskingdoms.equestrian.Bonding.register();
		// (0.5.45) stables at work: boarding, the horse market, the farrier
		Stables.register();
		Carriages.register();
		Rickshaws.register();
		Cabs.register();
		// 0.5.29: quests from villagers and kings, their caches and champions, relics (and their attachments)
		LOGGER.debug("quest attachments: {} {} {} {}", Quests.LOG, Quests.OFFER, Quests.MARK, QuestFoes.FOE);
		// 0.5.38: where newcomers and immigrants are from (skin tone by climate)
		LOGGER.debug("origin attachment: {}", SkinTones.ORIGIN);
		Quests.register();
		// 0.5.39: the townsfolk come over to you: questions, quests, requests, bargains, news
		Approaches.register();
		// 0.5.37: sorcery: camps' shamans and necromancers, courts' wizards, the risen dead, spell scrolls (and the thralls' attachment)
		Arcana.register();
		LOGGER.debug("thrall attachment: {}", Thralls.TAG);
		Thralls.register();
		Sorcery.register();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Farmers.clear();
			Gatherers.clear();
			Royals.clear();
			Wardens.clear();
			Focus.clear();
		});
	}
}
