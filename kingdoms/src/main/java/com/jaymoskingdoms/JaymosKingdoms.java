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
			.clientTrackingRange(10)
			.build(key));
		FabricDefaultAttributeRegistry.register(FOLK, Folk.guardAttributes());
		// the attachments (registered as their classes load)
		LOGGER.debug("kingdom attachments: {} {} {}", Factions.ALL, Factions.KEEPS, Reputation.REPUTATION);
		KingdomEvents.register();
		// barbarian camps, their raiders and raids (and their attachment)
		LOGGER.debug("camp attachment: {}", Camps.CAMPS);
		LOGGER.debug("illager attachment: {}", Illagers.TAG);
		Camps.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> KingdomCommands.register(dispatcher));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> KingdomEvents.forget(handler.player));
		// 0.3, builders and theft: the building catalogue, real-time construction, plots and the plot stake, crime
		LOGGER.debug("builder and theft attachments: {} {}", Works.WORKS, Theft.WANTED);
		Plots.register();
		Buildings.register();
		Construction.register();
		Theft.register();
		// 0.3, war: declarations, war parties, banners, peace (and its attachment)
		LOGGER.debug("war attachment: {}", WarData.WARS);
		Wars.register();
		// 0.4, combat: Better Combat's weapon moves for humanoid mobs, and folk's sidestep (only with Better Combat)
		MobCombat.register();
		// 0.4.1: ATi Structures' above-ground buildings in compounds (only with ATi loaded)
		AtiCompounds.register();
	}
}
