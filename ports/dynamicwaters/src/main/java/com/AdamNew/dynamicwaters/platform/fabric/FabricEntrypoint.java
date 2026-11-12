package com.AdamNew.dynamicwaters.platform.fabric;

import com.AdamNew.dynamicwaters.Config;
import com.AdamNew.dynamicwaters.DynamicWaters;
import com.AdamNew.dynamicwaters.FlowZones;
import com.AdamNew.dynamicwaters.SableCompat;
import com.AdamNew.dynamicwaters.events.IceFloeSpawner;
import com.AdamNew.dynamicwaters.events.SpawningEvents;
import com.AdamNew.dynamicwaters.network.SyncFlowZonesPacket;
import com.AdamNew.dynamicwaters.network.SyncMainRiversPacket;
import com.AdamNew.dynamicwaters.network.SyncSeedPacket;
import com.AdamNew.dynamicwaters.network.SyncVanillaFlowPacket;
import com.AdamNew.dynamicwaters.network.SyncWatershedsPacket;
import com.AdamNew.dynamicwaters.registry.ModBlocks;
import com.AdamNew.dynamicwaters.registry.ModEntities;
import com.AdamNew.dynamicwaters.registry.ModFluids;
import com.AdamNew.dynamicwaters.registry.ModItems;
import com.AdamNew.dynamicwaters.registry.ModParticles;
import com.AdamNew.dynamicwaters.registry.ModSounds;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.config.ModConfig;

public class FabricEntrypoint implements ModInitializer {
	private static void addRiverSpawns() {
		addRiverSpawn(net.minecraft.world.entity.EntityTypes.SALMON, 8, 1, 4);
		addRiverSpawn(net.minecraft.world.entity.EntityTypes.COD, 6, 1, 4);
		addRiverSpawn(net.minecraft.world.entity.EntityTypes.SQUID, 5, 1, 4);
		addRiverSpawn(net.minecraft.world.entity.EntityTypes.GLOW_SQUID, 2, 1, 3);
		addRiverSpawn(net.minecraft.world.entity.EntityTypes.DROWNED, 5, 1, 1);
	}

	private static void addRiverSpawn(net.minecraft.world.entity.EntityType<?> type, int weight, int min, int max) {
		net.fabricmc.fabric.api.biome.v1.BiomeModifications.addSpawn(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(),
				type.getCategory(), type, weight, min, max);
	}

	@Override
	public void onInitialize() {
		ConfigRegistry.INSTANCE.register(DynamicWaters.MODID, ModConfig.Type.COMMON, Config.SPEC);
		ModConfigEvents.loading(DynamicWaters.MODID).register(config -> {
			if (config.getSpec() == Config.SPEC) DynamicWaters.onConfigLoaded();
		});

		ModFluids.FLUIDS.attach();
		ModBlocks.BLOCKS.attach();
		ModItems.ITEMS.attach();
		ModParticles.PARTICLE_TYPES.attach();
		ModEntities.ENTITY_TYPES.attach();
		ModSounds.SOUND_EVENTS.attach();
		DynamicWaters.DENSITY_FUNCTIONS.attach();

		PayloadTypeRegistry.clientboundPlay().register(SyncSeedPacket.TYPE, SyncSeedPacket.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncWatershedsPacket.TYPE, SyncWatershedsPacket.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncVanillaFlowPacket.TYPE, SyncVanillaFlowPacket.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncMainRiversPacket.TYPE, SyncMainRiversPacket.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncFlowZonesPacket.TYPE, SyncFlowZonesPacket.STREAM_CODEC);

		FabricLoader.getInstance().getModContainer(DynamicWaters.MODID).ifPresent(container -> {
			for (DynamicWaters.BuiltInPack pack : DynamicWaters.builtInPacks()) {
				ResourceManagerHelper.registerBuiltinResourcePack(Identifier.fromNamespaceAndPath(DynamicWaters.MODID, pack.folder()),
						container, Component.literal(pack.title()), ResourcePackActivationType.ALWAYS_ENABLED);
			}
		});

		addRiverSpawns();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> DynamicWaters.onServerAboutToStart());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			DynamicWaters.onServerStarting();
			DynamicWaters.onServerStarted(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DynamicWaters.onServerStopped();
			FlowZones.onServerStopped();
		});
		ServerLevelEvents.LOAD.register((server, level) -> {
			DynamicWaters.onLevelLoad(level);
			FlowZones.onLevelLoad(level);
		});
		ServerLevelEvents.UNLOAD.register((server, level) -> FlowZones.onLevelUnload(level));
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			SableCompat.tickPhysicsShips(level);
			IceFloeSpawner.onLevelTick(level);
		});
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> SpawningEvents.onChunkLoad(level, chunk));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> DynamicWaters.onRegisterCommands(dispatcher));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			DynamicWaters.ModEvents.onPlayerLogin(handler.player);
			FlowZones.syncTo(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DynamicWaters.ModEvents.onPlayerLogout(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
			DynamicWaters.ModEvents.onPlayerRespawn(player);
			FlowZones.syncTo(player);
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			DynamicWaters.ModEvents.onPlayerChangeDimension(player);
			FlowZones.syncTo(player);
		});

		DynamicWaters.init();
	}
}
