package com.jaymospowers.armory;

import com.jaymospowers.core.ExplosionLog;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (BWM port) The mining charge, after Better With Mods' (its idea, not its code): a pack of explosive stuck to the face of
 * a block. Lit by redstone, a burning arrow, flint and steel or another blast, it hisses for {@link #FUSE} ticks and then
 * digs into the block it's stuck to: a 3x3x3 cube centred one block behind it, and one more block 3 deep, all dropped as
 * if mined (no crater, no lost blocks). Anything as blast-proof as obsidian stays. Whatever lives in the cube takes
 * {@link #CHARGE_DAMAGE}; other charges nearby go off a moment later.
 */
public final class MiningChargeBlock extends Block {
	/** Ticks from lighting to the blast (BWM: 80), and for a charge set off by another (plus up to that again at random). */
	static final int FUSE = 80, CHAIN_FUSE = 4;
	/** Damage to everything living in the dug cube (BWM's 45 killed anything; this is a bad hit, not a death). */
	static final float CHARGE_DAMAGE = 12.0F;
	/** Charges this many blocks away (in any direction) go off with this one. */
	static final int CHAIN_RADIUS = 2;
	/** How far the extra block behind the cube is. */
	static final int REACH = 3;
	/** Blocks at least this blast-proof are never dug (obsidian's 1200: obsidian, crying obsidian, anvils, bedrock...). */
	static final float RESISTANCE_LIMIT = 1200.0F;

	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/** Shapes by facing (the face it was put on: it sits against the block opposite). */
	private static final Map<Direction, VoxelShape> SHAPES = Map.of(
		Direction.UP, Block.box(2, 0, 2, 14, 5, 14),
		Direction.DOWN, Block.box(2, 11, 2, 14, 16, 14),
		Direction.NORTH, Block.box(2, 2, 11, 14, 14, 16),
		Direction.SOUTH, Block.box(2, 2, 0, 14, 14, 5),
		Direction.WEST, Block.box(11, 2, 2, 16, 14, 14),
		Direction.EAST, Block.box(0, 2, 2, 5, 14, 14));

	/** Who lit (or else placed) each charge, so the blast is theirs. Not saved: a charge lit before a restart blows up for no one. */
	private static final Map<ServerLevel, Map<BlockPos, UUID>> WHO = new WeakHashMap<>();

	public MiningChargeBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = this.defaultBlockState().setValue(FACING, context.getClickedFace());
		return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos behind = pos.relative(facing.getOpposite());
		return level.getBlockState(behind).isFaceSturdy(level, behind, facing);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		// a lit charge hangs on, so one whose block another charge dug out still goes off
		if (direction == state.getValue(FACING).getOpposite() && !state.getValue(LIT) && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		if (level instanceof ServerLevel server && placer instanceof Player) {
			WHO.computeIfAbsent(server, l -> new HashMap<>()).put(pos.immutable(), placer.getUUID());
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server) {
			Map<BlockPos, UUID> who = WHO.get(server);
			if (who != null) {
				who.remove(pos);
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	// --- lighting ---

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
		if (!old.is(this) && level.hasNeighborSignal(pos)) {
			light(level, pos, state, null, FUSE);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		if (level.hasNeighborSignal(pos)) {
			light(level, pos, state, null, FUSE);
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		}
		if (state.getValue(LIT)) {
			return InteractionResult.PASS;
		}
		light(level, pos, state, player, FUSE);
		if (stack.is(Items.FLINT_AND_STEEL)) {
			stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
		} else {
			stack.consume(1, player);
		}
		player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
		if (level instanceof ServerLevel server && projectile.isOnFire() && projectile.mayInteract(server, hit.getBlockPos())) {
			light(level, hit.getBlockPos(), state, projectile.getOwner() instanceof LivingEntity owner ? owner : null, FUSE);
		}
	}

	/** Another blast doesn't break it: it lights it with a short fuse, like TNT. */
	@Override
	protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> drops) {
		Explosion.BlockInteraction interaction = explosion.getBlockInteraction();
		if (interaction == Explosion.BlockInteraction.KEEP || interaction == Explosion.BlockInteraction.TRIGGER_BLOCK || !rule(level)) {
			super.onExplosionHit(state, level, pos, explosion, drops);
			return;
		}
		light(level, pos, state, explosion.getIndirectSourceEntity(), chainFuse(level.getRandom()));
	}

	private static boolean rule(ServerLevel level) {
		return level.getGameRules().get(GameRules.TNT_EXPLODES);
	}

	private static int chainFuse(RandomSource random) {
		return CHAIN_FUSE + random.nextInt(CHAIN_FUSE + 1);
	}

	/** Starts the fuse (once). {@code by}: who lit it, or null to keep whoever placed it. */
	void light(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, int fuse) {
		if (!(level instanceof ServerLevel server) || state.getValue(LIT) || !rule(server)) {
			return;
		}
		if (by instanceof Player) {
			WHO.computeIfAbsent(server, l -> new HashMap<>()).put(pos.immutable(), by.getUUID());
		}
		server.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
		server.scheduleTick(pos, this, fuse);
		server.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.2F);
		server.gameEvent(by, GameEvent.PRIME_FUSE, pos);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		Direction facing = state.getValue(FACING);
		double x = pos.getX() + 0.5 - facing.getStepX() * 0.15, y = pos.getY() + 0.5 - facing.getStepY() * 0.15,
			z = pos.getZ() + 0.5 - facing.getStepZ() * 0.15;
		level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.03, 0.0);
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0.0, 0.01, 0.0);
		}
	}

	// --- the blast ---

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(LIT)) {
			detonate(level, pos, state);
		}
	}

	private void detonate(ServerLevel level, BlockPos pos, BlockState state) {
		Map<BlockPos, UUID> who = WHO.get(level);
		UUID id = who == null ? null : who.remove(pos);
		Entity found = id == null ? null : level.getEntity(id);
		LivingEntity igniter = found instanceof LivingEntity living ? living : null;
		level.removeBlock(pos, false);

		Direction back = state.getValue(FACING).getOpposite();
		BlockPos centre = pos.relative(back);
		level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F,
			(1.0F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2F) * 0.7F);
		level.sendParticles(ParticleTypes.EXPLOSION, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 3, 0.6, 0.6, 0.6, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.05);
		ExplosionLog.log(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), "jaymospowers:mining_charge", igniter instanceof Player player ? player : null);

		// the living first, before the cube opens and they fall into it
		AABB area = new AABB(centre).inflate(1.0);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
			victim.hurtServer(level, level.damageSources().explosion(null, igniter), CHARGE_DAMAGE);
		}
		// the rest of the line, lit before the digging takes their blocks (a lit charge hangs on)
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-CHAIN_RADIUS, -CHAIN_RADIUS, -CHAIN_RADIUS), pos.offset(CHAIN_RADIUS, CHAIN_RADIUS, CHAIN_RADIUS))) {
			BlockState other = level.getBlockState(p);
			if (other.getBlock() instanceof MiningChargeBlock charge) {
				charge.light(level, p.immutable(), other, igniter, chainFuse(level.getRandom()));
			}
		}
		ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) {
			dig(level, p.immutable(), igniter, tool);
		}
		dig(level, pos.relative(back, REACH), igniter, tool);
	}

	/** Mines one block for its drops (as an iron pickaxe would), unless it's air, fluid, unbreakable, too strong or a charge. */
	private static void dig(ServerLevel level, BlockPos pos, @Nullable LivingEntity igniter, ItemStack tool) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.liquid() || state.getBlock() instanceof MiningChargeBlock || state.getDestroySpeed(level, pos) < 0.0F
			|| state.getBlock().getExplosionResistance() >= RESISTANCE_LIMIT) {
			return;
		}
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
		Block.dropResources(state, level, pos, blockEntity, igniter, tool);
		level.removeBlock(pos, false);
		level.gameEvent(igniter, GameEvent.BLOCK_DESTROY, pos);
	}
}
