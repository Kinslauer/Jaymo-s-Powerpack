package com.jaymoskingdoms.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * A covered pit's lid: laid over a hole the diggers ({@link Digger}) dug and dressed as the ground it replaced
 * ({@link Ground}: it is drawn with that block's own model, so nothing sets it apart). Anything with weight that walks onto
 * it goes through, to the stakes and the digger waiting below; a careful walker who is sneaking does not break it. A
 * watchful eye finds it first ({@link Warrens}).
 */
public class TrapCoverBlock extends Block {
	public static final EnumProperty<Ground> LOOK = EnumProperty.create("look", Ground.class);

	/** The ground a lid can pass for. */
	public enum Ground implements StringRepresentable {
		GRASS("grass", Blocks.GRASS_BLOCK, MapColor.GRASS),
		DIRT("dirt", Blocks.DIRT, MapColor.DIRT),
		COARSE_DIRT("coarse_dirt", Blocks.COARSE_DIRT, MapColor.DIRT),
		ROOTED_DIRT("rooted_dirt", Blocks.ROOTED_DIRT, MapColor.DIRT),
		PODZOL("podzol", Blocks.PODZOL, MapColor.PODZOL),
		MYCELIUM("mycelium", Blocks.MYCELIUM, MapColor.COLOR_PURPLE),
		MOSS("moss", Blocks.MOSS_BLOCK, MapColor.COLOR_GREEN),
		MUD("mud", Blocks.MUD, MapColor.TERRACOTTA_CYAN),
		SAND("sand", Blocks.SAND, MapColor.SAND),
		RED_SAND("red_sand", Blocks.RED_SAND, MapColor.COLOR_ORANGE),
		GRAVEL("gravel", Blocks.GRAVEL, MapColor.STONE),
		SNOW("snow", Blocks.SNOW_BLOCK, MapColor.SNOW),
		STONE("stone", Blocks.STONE, MapColor.STONE);

		private final String name;
		final Block block;
		final MapColor color;

		Ground(String name, Block block, MapColor color) {
			this.name = name;
			this.block = block;
			this.color = color;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		/** The look that passes for {@code state}, or null if a lid can't pass for it. */
		static @Nullable Ground of(BlockState state) {
			for (Ground g : values()) {
				if (state.is(g.block)) {
					return g;
				}
			}
			return null;
		}
	}

	public TrapCoverBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(LOOK, Ground.GRASS));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LOOK);
	}

	/** A lid dressed as the ground beside {@code pos} (the first neighbour it can pass for), or null if none is. */
	static @Nullable BlockState matching(BlockGetter level, BlockPos pos) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			Ground g = Ground.of(level.getBlockState(pos.relative(side)));
			if (g != null) {
				return Creatures.TRAP_COVER.defaultBlockState().setValue(LOOK, g);
			}
		}
		return null;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState like = matching(context.getLevel(), context.getClickedPos());
		return like != null ? like : this.defaultBlockState();
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (level instanceof ServerLevel server && entity instanceof LivingEntity living && !living.isShiftKeyDown() && living.getBbWidth() > 0.3F
			 && !(living instanceof Digger)) {
			server.playSound(null, pos, SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
			server.destroyBlock(pos, false);
			Warrens.alarm(server, pos, living);
		}
		super.stepOn(level, pos, state, entity);
	}
}
