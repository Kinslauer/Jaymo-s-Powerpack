package com.jaymoskingdoms.lands;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * The three blocks of the new lands: steaming calcite (the steam valley's terraces), smoldering basalt (the ashlands' vents,
 * which burns the feet like magma) and the salt crust of the salt pans (dug with a shovel, it gives salt).
 */
public final class LandBlocks {
	public static Block STEAMING_CALCITE, SMOLDERING_BASALT, SALT_CRUST;

	/** Steam over a terrace's water: how often (1 in n) a block lets a puff go in a client tick, and the smoke of a vent. */
	static final int STEAM_ONE_IN = 4, VENT_ONE_IN = 3, VENT_SPARK_ONE_IN = 12;
	/** A vent's light. */
	static final int VENT_LIGHT = 4;

	private LandBlocks() {
	}

	static void register() {
		STEAMING_CALCITE = block("steaming_calcite", SteamingCalcite::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE));
		SMOLDERING_BASALT = block("smoldering_basalt", SmolderingBasalt::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BASALT)
			.mapColor(MapColor.COLOR_BLACK).lightLevel(state -> VENT_LIGHT));
		SALT_CRUST = block("salt_crust", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.6F)
			.sound(SoundType.CALCITE));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.insertAfter(Items.CALCITE, STEAMING_CALCITE.asItem());
			output.insertAfter(Items.BASALT, SMOLDERING_BASALT.asItem());
			output.insertAfter(Items.RED_SAND, SALT_CRUST.asItem());
		});
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
		ResourceKey<Item> item = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Registry.register(BuiltInRegistries.ITEM, item, new BlockItem(block, new Item.Properties().setId(item).useBlockDescriptionPrefix()));
		return block;
	}

	/** Calcite that breathes: a puff of steam now and then over water or open air, for the pools of the steam valley. */
	public static final class SteamingCalcite extends Block {
		SteamingCalcite(Properties properties) {
			super(properties);
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			BlockPos above = pos.above();
			boolean open = level.getBlockState(above).isAir() || level.getFluidState(above).is(FluidTags.WATER);
			if (open && random.nextInt(STEAM_ONE_IN) == 0) {
				level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.05, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
					0.0, 0.03 + random.nextDouble() * 0.03, 0.0);
			}
		}
	}

	/** Basalt over a vent: hot underfoot like magma, smoking, and now and then a spark. */
	public static final class SmolderingBasalt extends MagmaBlock {
		SmolderingBasalt(Properties properties) {
			super(properties);
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (!level.getBlockState(pos.above()).isAir()) {
				return;
			}
			if (random.nextInt(VENT_ONE_IN) == 0) {
				level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.0, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
					0.0, 0.05, 0.0);
			}
			if (random.nextInt(VENT_SPARK_ONE_IN) == 0) {
				level.addParticle(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
			}
		}
	}
}
