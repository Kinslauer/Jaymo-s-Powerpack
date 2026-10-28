package com.jaymospowers.frost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Brittle ice (26.4's, built for 26.3): thin ice that starts to crack the moment anything living stands on it, cracks
 * further every {@link #CRACK_TICKS} ticks whether or not they stay, and gives way at the last crack (into the water under it, if
 * there is any). Frostbites and strays are light enough on their feet to cross it. Only silk touch takes it whole. It lies as a
 * crust over frozen lakes in the snow ({@code ThinIceFeature}).
 */
public class BrittleIceBlock extends HalfTransparentBlock {
	// ---- tuning ----
	/** Ticks between cracks; it breaks after {@link #MAX_CRACKS}. */
	public static final int CRACK_TICKS = 10;
	public static final int MAX_CRACKS = 3;

	public static final IntegerProperty CRACKS = IntegerProperty.create("cracks", 0, MAX_CRACKS);

	public BrittleIceBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(CRACKS, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CRACKS);
	}

	/** Whether {@code entity} is heavy enough to crack it. */
	public static boolean cracks(Entity entity) {
		return entity instanceof LivingEntity && !entity.getType().equals(Frost.FROSTBITE) && !entity.getType().equals(EntityTypes.STRAY);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (!level.isClientSide() && cracks(entity) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
			level.scheduleTick(pos, this, CRACK_TICKS);
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int cracks = state.getValue(CRACKS);
		if (cracks < MAX_CRACKS) {
			level.setBlock(pos, state.setValue(CRACKS, cracks + 1), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 0.8F, 1.4F + random.nextFloat() * 0.3F);
			level.scheduleTick(pos, this, CRACK_TICKS);
			return;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16, 0.3, 0.2, 0.3, 0.05);
		level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.1F);
		// (over a lake it opens on the water; on land, a hole)
		level.setBlockAndUpdate(pos, level.getFluidState(pos.below()).is(FluidTags.WATER) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
	}
}
