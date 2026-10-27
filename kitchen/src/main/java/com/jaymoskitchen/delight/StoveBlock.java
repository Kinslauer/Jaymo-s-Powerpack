package com.jaymoskitchen.delight;

import net.fabricmc.fabric.api.registry.LandPathTypeRegistry;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (Farmer's Delight's stove, MIT; its model and art, our own code.) A brick stove with six grill spots on top that
 * cook what a campfire cooks (right-click food onto it). It's lit when placed; flint and steel or a fire charge light
 * it, a shovel or a bucket of water puts it out, and it burns what steps on it while it's lit. Lit, it heats cookware
 * set on it ({@link Delight#HEAT_SOURCES}), and sneak-clicking it with an empty hand opens the same dish list as a
 * campfire (see {@link com.jaymoskitchen.cooking.Station#of}).
 */
public class StoveBlock extends BaseEntityBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	/** Light a lit stove gives off. */
	public static final int LIT_LIGHT = 13;
	/** Damage of stepping on a lit stove. */
	private static final float BURN_DAMAGE = 1.0F;
	/** The cooking surface: what burns you, and what must be clear above for food to cook. */
	private static final VoxelShape GRILLING_AREA = Block.box(3.0, 0.0, 3.0, 13.0, 1.0, 13.0);

	public StoveBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(LIT, false));
		LandPathTypeRegistry.registerDynamic(this, (state, level, pos, neighbour) -> state.getValue(LIT) ? PathType.FIRE : null);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HorizontalDirectionalBlock.FACING, LIT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite()).setValue(LIT, true);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StoveBlockEntity(pos, state);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, DelightHome.STOVE_ENTITY, (l, pos, s, stove) -> {
			if (l instanceof ServerLevel server) {
				StoveBlockEntity.serverTick(server, pos, s, stove);
			}
			if (s.getValue(LIT)) {
				stove.particleTick();
			}
		});
	}

	@Override
	public InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		InteractionResult result = state.getValue(LIT) ? tryExtinguish(held, state, level, pos, player, hand) : tryIgnite(held, state, level, pos, player, hand);
		if (result != InteractionResult.TRY_WITH_EMPTY_HAND) {
			return result;
		}
		return tryPlaceFood(held, state, level, pos, player);
	}

	private InteractionResult tryIgnite(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
		if (held.getItem() instanceof FlintAndSteelItem) {
			if (!level.isClientSide()) {
				level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
			}
			setLit(player, level, pos, state, true);
			held.hurtAndBreak(1, player, hand.asEquipmentSlot());
			return InteractionResult.SUCCESS;
		}
		if (held.getItem() instanceof FireChargeItem) {
			if (!level.isClientSide()) {
				level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2F + 1.0F);
			}
			setLit(player, level, pos, state, true);
			if (!player.getAbilities().instabuild) {
				held.shrink(1);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	private InteractionResult tryExtinguish(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
		if (held.is(ItemTags.SHOVELS)) {
			if (!level.isClientSide()) {
				level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, pos, 0);
			}
			setLit(player, level, pos, state, false);
			held.hurtAndBreak(1, player, hand.asEquipmentSlot());
			return InteractionResult.SUCCESS;
		}
		if (held.is(ConventionalItemTags.WATER_BUCKETS)) {
			if (!level.isClientSide()) {
				level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			setLit(player, level, pos, state, false);
			ItemStackTemplate remainder = held.getCraftingRemainder();
			if (!player.getAbilities().instabuild && remainder != null) {
				player.setItemInHand(hand, remainder.create());
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	private InteractionResult tryPlaceFood(ItemStack held, BlockState state, Level level, BlockPos pos, Player player) {
		if (topCovered(level, pos, state) || !(level.getBlockEntity(pos) instanceof StoveBlockEntity stove)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.CONSUME;
		}
		var recipe = stove.recipeFor(held);
		if (recipe.isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!stove.placeFood(player, player.getAbilities().instabuild ? held.copy() : held, recipe.get())) {
			return InteractionResult.CONSUME;
		}
		level.playSound(null, pos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Lights or puts out the stove (the entity is told so it can sync). */
	public void setLit(@Nullable Entity actor, LevelAccessor level, BlockPos pos, BlockState state, boolean lit) {
		if (level.getBlockEntity(pos) instanceof StoveBlockEntity stove) {
			stove.litChanged();
		}
		if (level.isClientSide()) {
			return;
		}
		level.setBlock(pos, state.setValue(LIT, lit), 11);
		level.gameEvent(actor, GameEvent.BLOCK_CHANGE, pos);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (state.getValue(LIT) && entity instanceof LivingEntity && !entity.isSteppingCarefully()
			&& entity.getBoundingBox().intersects(GRILLING_AREA.bounds().move(pos.above()))
			&& level instanceof ServerLevel server) {
			entity.hurtServer(server, new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DelightHome.STOVE_BURN)), BURN_DAMAGE);
		}
		super.stepOn(level, pos, state, entity);
	}

	/** Whether something above the stove is in the way of the grill spots (food can't cook, and is dropped). */
	public static boolean topCovered(Level level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof StoveBlock)) {
			return false;
		}
		BlockPos above = pos.above();
		return Shapes.joinIsNotEmpty(GRILLING_AREA, level.getBlockState(above).getShape(level, above), BooleanOp.AND);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		double x = pos.getX() + 0.5;
		double y = pos.getY();
		double z = pos.getZ() + 0.5;
		if (random.nextInt(10) == 0) {
			level.playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
		}
		Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
		double along = random.nextDouble() * 0.6 - 0.3;
		double dx = facing.getAxis() == Direction.Axis.X ? facing.getStepX() * 0.52 : along;
		double dy = random.nextDouble() * 6.0 / 16.0;
		double dz = facing.getAxis() == Direction.Axis.Z ? facing.getStepZ() * 0.52 : along;
		level.addParticle(ParticleTypes.SMOKE, x + dx, y + dy, z + dz, 0, 0, 0);
		level.addParticle(ParticleTypes.FLAME, x + dx, y + dy, z + dz, 0, 0, 0);
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
	}
}
