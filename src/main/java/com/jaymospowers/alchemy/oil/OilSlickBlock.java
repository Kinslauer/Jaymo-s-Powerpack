package com.jaymospowers.alchemy.oil;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Spilled oil (Hunter's Road, patch 7, "T-C5"): a thin dark slick on the ground, where an oil pot was broken, a flask was
 * poured out (sneak and use it on the ground) or a thrown oil landed. It <b>bursts into flame</b> when anything alight is near
 * it: fire, lava, a torch, a lit campfire, furnace or candle, a burning creature stepping in it, or a flaming arrow
 * ({@link #nearFlame}). The flare is real fire (vanilla's own fire block, which is also what Forest Fire and the settlements' fire
 * alarms watch for, so a slick lit near a village is cried out like any fire) and it runs on to the slicks beside it
 * ({@link #SPREAD_DELAY}). Rain washes it away, and left alone it slowly dries.
 */
public final class OilSlickBlock extends Block {
	// ---- tuning ----
	/** Ticks between looks for flame; ticks a flare takes to reach the next slick. */
	public static final int WATCH_EVERY = 10, SPREAD_DELAY = 3;
	/** One random tick in this many dries it up. */
	public static final int DRY_ODDS = 12;
	/** The share of normal speed a creature keeps in it. */
	public static final double DRAG = 0.8;
	/** Seconds a creature caught in the flare burns, and the hurt the flare itself does. */
	public static final float BURN_SECONDS = 6.0F;
	public static final float FLARE_HURT = 2.0F;

	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

	public static Block SLICK;

	private OilSlickBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("oil_slick"));
		SLICK = Registry.register(BuiltInRegistries.BLOCK, key, new OilSlickBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
			.instabreak().noCollision().noOcclusion().replaceable().noLootTable().sound(SoundType.SLIME_BLOCK).pushReaction(PushReaction.POPPED)
			.randomTicks().setId(key)));
	}

	/**
	 * Spills oil round {@code centre}, {@code radius} blocks out (0 is one block): a slick on every free spot with firm ground
	 * under it. Returns whether any went down.
	 */
	public static boolean spill(ServerLevel level, BlockPos centre, int radius) {
		boolean any = false;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -1, -radius), centre.offset(radius, 1, radius))) {
			if (Math.abs(pos.getX() - centre.getX()) + Math.abs(pos.getZ() - centre.getZ()) > radius + 1) {
				continue;
			}
			BlockState here = level.getBlockState(pos);
			if ((here.isAir() || here.canBeReplaced() && !here.is(SLICK) && here.getFluidState().isEmpty()) && SLICK.defaultBlockState().canSurvive(level, pos)) {
				level.setBlockAndUpdate(pos, SLICK.defaultBlockState());
				any = true;
			}
		}
		return any;
	}

	/** Whether something alight is within one block of {@code pos} (all round, corners too). */
	public static boolean nearFlame(Level level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			if (isFlame(level.getBlockState(p))) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code s} is something alight: fire, lava, a torch, a lit campfire, furnace or candle. */
	public static boolean isFlame(BlockState s) {
		return s.is(BlockTags.FIRE) || s.is(Blocks.LAVA) || s.getBlock() instanceof TorchBlock || s.getBlock() instanceof WallTorchBlock
			|| s.is(BlockTags.CAMPFIRES) && s.hasProperty(CampfireBlock.LIT) && s.getValue(CampfireBlock.LIT)
			|| s.getBlock() instanceof AbstractCandleBlock && AbstractCandleBlock.isLit(s)
			|| s.getBlock() instanceof AbstractFurnaceBlock && s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
	}

	/** The slick at {@code pos} flares: it becomes fire, whoever stands in it burns, and the slicks beside it are lit a moment later. */
	public static void flare(ServerLevel level, BlockPos pos) {
		if (!level.getBlockState(pos).is(SLICK)) {
			return;
		}
		BlockState fire = Blocks.FIRE.defaultBlockState();
		level.setBlockAndUpdate(pos, fire.canSurvive(level, pos) ? fire : Blocks.AIR.defaultBlockState());
		for (Entity e : level.getEntities((Entity) null, new AABB(pos).inflate(0.2, 0.5, 0.2), x -> x instanceof LivingEntity)) {
			e.igniteForSeconds(BURN_SECONDS);
			e.hurtServer(level, level.damageSources().inFire(), FLARE_HURT);
		}
		level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 8, 0.3, 0.15, 0.3, 0.03);
		level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5F, 1.2F);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos next = pos.relative(d);
			if (level.getBlockState(next).is(SLICK)) {
				level.scheduleTick(next, SLICK, SPREAD_DELAY);
			}
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return Block.canSupportCenter(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		return this.canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
		if (!old.is(this) && level instanceof ServerLevel server) {
			server.scheduleTick(pos, this, WATCH_EVERY);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.isRainingAt(pos.above())) {
			level.removeBlock(pos, false);
		} else if (nearFlame(level, pos)) {
			flare(level, pos);
		} else {
			level.scheduleTick(pos, this, WATCH_EVERY);
		}
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(DRY_ODDS) == 0) {
			level.removeBlock(pos, false);
		}
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean intersects) {
		entity.makeStuckInBlock(state, new Vec3(DRAG, 1.0, DRAG));
		if (entity.isOnFire() && level instanceof ServerLevel server) {
			flare(server, pos);
		}
	}

	@Override
	protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
		if (projectile.isOnFire() && level instanceof ServerLevel server) {
			flare(server, hit.getBlockPos());
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(24) == 0) {
			level.addParticle(ParticleTypes.FALLING_OBSIDIAN_TEAR, pos.getX() + random.nextDouble(), pos.getY() + 0.1, pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
		}
	}
}
