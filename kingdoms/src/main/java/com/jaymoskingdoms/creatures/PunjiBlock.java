package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * GT New Horizons picks, patch 6: <b>punji stakes</b>, sticks whittled to points and set in the ground, points up (the
 * old trap of hunters and of anyone holding a line; Andy: "diggers can use these too"). Like a berry bush, but sharper:
 * whatever walks into them is slowed and stabbed at every step, and one who drops onto them falls harder, as onto a
 * stalagmite. Mobs go round them when they can (a damaging path type, {@link Creatures}). Diggers set them in their pits
 * and bolt-holes ({@link Warrens}) and know where they are; barbarian camps ring their palisade with them.
 */
public class PunjiBlock extends Block {
	/** The slowing (what's left of movement), a step's stab, and a fall's extra distance and harm (as a stalagmite's). */
	static final double CRAWL = 0.7;
	static final float STAB = 2.0F, FALL_EXTRA = 2.0F, FALL_TIMES = 2.0F;
	/** Moving at least this much (blocks a tick, sideways) counts as a step. */
	static final double STEP = 0.003;

	/** A body that has fallen this far (blocks) isn't caught by the stakes until it lands. */
	static final double FALLING = 1.0;
	public static final ResourceKey<DamageType> PUNJI = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosKingdoms.id("punji"));
	/** What a body stands on among the stakes (low enough to step onto, high enough that a fall lands on the stakes and not the
	 * ground under them), and what's drawn and picked. */
	private static final VoxelShape BASE = Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0), STAKES = Block.box(1.0, 0.0, 1.0, 15.0, 10.0, 15.0);

	public PunjiBlock(Properties properties) {
		super(properties);
	}

	static DamageSource stab(Level level) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(PUNJI));
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean intersects) {
		// a digger knows where its own stakes are
		if (!(entity instanceof LivingEntity) || entity.isSpectator() || entity instanceof Digger) {
			return;
		}
		// a body still falling lands on the stakes first (being caught resets the fall, and the fall is what hurts)
		if (entity.fallDistance < FALLING) {
			entity.makeStuckInBlock(state, new Vec3(CRAWL, 0.75, CRAWL));
		}
		if (level instanceof ServerLevel server) {
			Vec3 moved = entity.isClientAuthoritative() ? entity.getKnownMovement() : entity.oldPosition().subtract(entity.position());
			if (Math.abs(moved.x) >= STEP || Math.abs(moved.z) >= STEP) {
				entity.hurtServer(server, stab(level), STAB);
			}
		}
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		entity.causeFallDamage(fallDistance + FALL_EXTRA, FALL_TIMES, stab(level));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return STAKES;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return BASE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
		BlockState neighbourState, RandomSource random) {
		return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}
}
