package com.jaymospowers.aether.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A campfire lit with an ambrosium shard (not flint and steel): it burns golden and bright, and a shovel puts it out. It does not cook. */
public class AmbrosiumCampfireBlock extends Block {
	// ---- tuning ----
	public static final int LIGHT = 15;
	/** Fire damage to whatever stands in a lit one. */
	public static final float DAMAGE = 1.0F;

	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 7.0, 16.0);

	public AmbrosiumCampfireBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		boolean lit = state.getValue(LIT);
		if (!lit && stack.is(com.jaymospowers.aether.AetherItems.AMBROSIUM_SHARD)) {
			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(LIT, true), 3);
				level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.2F);
				stack.consume(1, player);
			}
			return InteractionResult.SUCCESS;
		}
		if (lit && stack.is(ItemTags.SHOVELS)) {
			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(LIT, false), 3);
				level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean intersects) {
		if (state.getValue(LIT) && entity instanceof LivingEntity && level instanceof ServerLevel server) {
			entity.hurtServer(server, server.damageSources().inFire(), DAMAGE);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(LIT)) {
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.5, pos.getZ() + 0.3 + random.nextDouble() * 0.4,
					0.0, 0.03, 0.0);
			}
			if (random.nextInt(8) == 0) {
				level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 0.0, 0.06, 0.0);
			}
		}
	}
}
