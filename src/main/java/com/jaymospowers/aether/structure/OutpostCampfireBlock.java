package com.jaymospowers.aether.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.core.GlobalPos;

/**
 * The outpost campfire: a firepit two blocks by two (four parts, one block each). Right-click lights all four and the player
 * remembers it ({@link OutpostTracker}): dying with no bed or anchor, they wake beside it.
 */
public class OutpostCampfireBlock extends Block {
	// ---- tuning ----
	/** Light of a lit fire. */
	public static final int LIGHT = 15;

	/** Which part this block is: the east half (x + 1) and the south half (z + 1) of the 2x2. */
	public static final BooleanProperty EAST_PART = BooleanProperty.create("east");
	public static final BooleanProperty SOUTH_PART = BooleanProperty.create("south");
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);

	public OutpostCampfireBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(EAST_PART, false).setValue(SOUTH_PART, false).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(EAST_PART, SOUTH_PART, LIT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** The north-west part of the 2x2 this part belongs to. */
	public static BlockPos origin(BlockState state, BlockPos pos) {
		return pos.offset(state.getValue(EAST_PART) ? -1 : 0, 0, state.getValue(SOUTH_PART) ? -1 : 0);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos at = context.getClickedPos();
		for (int dx = 0; dx < 2; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				if (!context.getLevel().getBlockState(at.offset(dx, 0, dz)).canBeReplaced()) {
					return null;
				}
			}
		}
		return this.defaultBlockState();
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
		for (int dx = 0; dx < 2; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				if (dx + dz > 0) {
					level.setBlock(pos.offset(dx, 0, dz), this.defaultBlockState().setValue(EAST_PART, dx == 1).setValue(SOUTH_PART, dz == 1), 3);
				}
			}
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos origin = origin(state, pos);
		boolean lit = state.getValue(LIT);
		for (int dx = 0; dx < 2; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				BlockPos p = origin.offset(dx, 0, dz);
				BlockState part = server.getBlockState(p);
				if (part.is(this)) {
					server.setBlock(p, part.setValue(LIT, true), 3);
					server.sendParticles(ParticleTypes.FLAME, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, lit ? 0 : 6, 0.25, 0.1, 0.25, 0.02);
				}
			}
		}
		if (!lit) {
			server.playSound(null, origin, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		boolean added = OutpostTracker.add(sp, GlobalPos.of(server.dimension(), origin.immutable()));
		sp.sendOverlayMessage(Component.translatable(added ? "message.jaymospowers.outpost.added" : "message.jaymospowers.outpost.known"));
		return InteractionResult.SUCCESS;
	}

	@Override
	public net.minecraft.world.level.block.state.BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && !player.isCreative()) {
			Block.popResource(level, pos, new ItemStack(this));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		BlockPos origin = origin(state, pos);
		for (int dx = 0; dx < 2; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				BlockPos p = origin.offset(dx, 0, dz);
				if (!p.equals(pos) && level.getBlockState(p).is(this)) {
					level.removeBlock(p, false);
				}
			}
		}
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.Entity entity,
		net.minecraft.world.entity.InsideBlockEffectApplier applier, boolean intersects) {
		if (state.getValue(LIT) && entity instanceof LivingEntity && level instanceof ServerLevel server) {
			entity.hurtServer(server, server.damageSources().inFire(), 1.0F);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(LIT) && random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5 + random.nextDouble() * 0.3 - 0.15, pos.getY() + 0.9,
				pos.getZ() + 0.5 + random.nextDouble() * 0.3 - 0.15, 0.0, 0.07, 0.0);
			level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.5, pos.getZ() + 0.3 + random.nextDouble() * 0.4,
				0.0, 0.02, 0.0);
		}
	}
}
