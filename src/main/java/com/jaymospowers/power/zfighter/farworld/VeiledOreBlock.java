package com.jaymospowers.power.zfighter.farworld;

import com.jaymospowers.tinkering.Tinkering;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.54) Ore on a far world, passing for the plain stone round it (the {@link Host}) until it is tapped with a pick or
 * its world's chart has been read ({@link FarWorlds#reveal}). It remembers which ore it is ({@link Kind}); deep ore (deepslate
 * hosts) comes back as the deepslate kind. It is never broken as it is: a blow only shows it, and it drops nothing.
 */
public final class VeiledOreBlock extends Block {
	/** The ores that can lie veiled, with the vanilla blocks they come back as. */
	public enum Kind implements StringRepresentable {
		COAL, IRON, COPPER, GOLD, REDSTONE, LAPIS, DIAMOND, EMERALD, NETHER_GOLD, QUARTZ, DEBRIS, SILVER;

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}

		/** The ore block this is, in its deepslate kind when {@code deep}. */
		public BlockState real(boolean deep) {
			Block block = switch (this) {
				case COAL -> deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
				case IRON -> deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
				case COPPER -> deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
				case GOLD -> deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE;
				case REDSTONE -> deep ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE;
				case LAPIS -> deep ? Blocks.DEEPSLATE_LAPIS_ORE : Blocks.LAPIS_ORE;
				case DIAMOND -> deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE;
				case EMERALD -> deep ? Blocks.DEEPSLATE_EMERALD_ORE : Blocks.EMERALD_ORE;
				case NETHER_GOLD -> Blocks.NETHER_GOLD_ORE;
				case QUARTZ -> Blocks.NETHER_QUARTZ_ORE;
				case DEBRIS -> Blocks.ANCIENT_DEBRIS;
				case SILVER -> deep ? Tinkering.DEEPSLATE_SILVER_ORE : Tinkering.SILVER_ORE;
			};
			return block.defaultBlockState();
		}

		/** Which ore {@code state} is, or null if it is not one that veils. */
		public static @Nullable Kind of(BlockState state) {
			if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
				return COAL;
			} else if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) {
				return IRON;
			} else if (state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)) {
				return COPPER;
			} else if (state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE)) {
				return GOLD;
			} else if (state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) {
				return REDSTONE;
			} else if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)) {
				return LAPIS;
			} else if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
				return DIAMOND;
			} else if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) {
				return EMERALD;
			} else if (state.is(Blocks.NETHER_GOLD_ORE)) {
				return NETHER_GOLD;
			} else if (state.is(Blocks.NETHER_QUARTZ_ORE)) {
				return QUARTZ;
			} else if (state.is(Blocks.ANCIENT_DEBRIS)) {
				return DEBRIS;
			} else if (Tinkering.SILVER_ORE != null && (state.is(Tinkering.SILVER_ORE) || state.is(Tinkering.DEEPSLATE_SILVER_ORE))) {
				return SILVER;
			}
			return null;
		}

		/** Whether {@code state} is the deepslate kind of its ore. */
		public static boolean deep(BlockState state) {
			return state.is(Blocks.DEEPSLATE_COAL_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)
				|| state.is(Blocks.DEEPSLATE_GOLD_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)
				|| state.is(Blocks.DEEPSLATE_DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)
				|| Tinkering.DEEPSLATE_SILVER_ORE != null && state.is(Tinkering.DEEPSLATE_SILVER_ORE);
		}
	}

	/** The stone it passes for (a texture each). */
	public enum Host implements StringRepresentable {
		STONE, DEEPSLATE, NETHERRACK, BLACKSTONE;

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final EnumProperty<Kind> ORE = EnumProperty.create("ore", Kind.class);
	public static final EnumProperty<Host> HOST = EnumProperty.create("host", Host.class);

	public VeiledOreBlock(Block.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(ORE, Kind.IRON).setValue(HOST, Host.STONE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ORE, HOST);
	}

	/** The ore this shows as when revealed. */
	public static BlockState revealed(BlockState veiled) {
		return veiled.getValue(ORE).real(veiled.getValue(HOST) == Host.DEEPSLATE);
	}
}
