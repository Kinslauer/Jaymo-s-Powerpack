package com.jaymospowers.eden;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.sky.SkyPockets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.MapColor;

/**
 * What lies inside the islands: caves worm through the Aether's own islands ({@link EdenFeature.Kind#ISLAND_CAVES}), and in
 * some of them, and in some hollow islands, the stone breathes out the green haze of the Hestveil's gas caves from vents. A
 * vent poisons whoever stands within {@link #REACH} blocks without a sky mask, exactly as the haze below does
 * ({@link SkyPockets#gas}). The Stone Garden also gets the pack's loose rocks, which the overworld's biomes have but this
 * country's did not.
 */
public final class EdenCaves {
	/** How far (blocks) a gas vent's haze reaches, and how often (ticks) it is felt. */
	public static final int REACH = 6, EVERY = 20;
	/** Hardness of a gas vent. */
	public static final float VENT_HARDNESS = 1.5F;
	/** The Aether's land biomes, whose islands the caves are worn into. */
	private static final String[] ISLAND_BIOMES = {"shimmering_basin", "flourishing_field", "verdant_woods", "shrouded_forest", "violet_highwoods",
		"turquoise_forest", "glistening_swamp", "magnetic_scar", "frozen_lakes", "sheer_tundra", "enduring_woodland", "frigid_sierra",
		"battleground_wastes", "contaminated_jungle", "hanging_roots", "mistsea", "rustworks", "gilded_ridge", "sunroot_grove"};

	public static Block GAS_VENT;

	private EdenCaves() {
	}

	/** A pocked stone that breathes a poison haze (felt by {@link #init}'s tick) and puffs it up where it can be seen. */
	static final class GasVentBlock extends Block {
		GasVentBlock(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(4) == 0) {
				level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.0,
					pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.03, 0.0);
			}
		}
	}

	public static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("gas_vent"));
		GAS_VENT = Registry.register(BuiltInRegistries.BLOCK, key, new GasVentBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN)
			.strength(VENT_HARDNESS).sound(SoundType.TUFF).requiresCorrectToolForDrops().setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("gas_vent"));
		Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(GAS_VENT, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> output.accept(item));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isAlive() && !player.isSpectator() && nearVent(player)) {
					SkyPockets.gas(player, player.level());
				}
			}
		});

		List<ResourceKey<Biome>> islands = new ArrayList<>();
		for (String biome : ISLAND_BIOMES) {
			islands.add(ResourceKey.create(Registries.BIOME, JaymosPowers.id(biome)));
		}
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(islands), GenerationStep.Decoration.UNDERGROUND_DECORATION, placed("island_caves"));
		// the Stone Garden's loose rocks (the placed feature is the pack's own, from the ground package)
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(ResourceKey.create(Registries.BIOME, JaymosPowers.id("stone_garden"))),
			GenerationStep.Decoration.VEGETAL_DECORATION, placed("loose_rocks"));
	}

	private static ResourceKey<PlacedFeature> placed(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id(name));
	}

	private static boolean nearVent(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-REACH, -REACH, -REACH), at.offset(REACH, REACH, REACH))) {
			if (level.getBlockState(pos).is(GAS_VENT)) {
				return true;
			}
		}
		return false;
	}
}
