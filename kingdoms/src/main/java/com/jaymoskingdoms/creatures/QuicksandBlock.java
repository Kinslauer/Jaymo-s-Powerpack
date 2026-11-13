package com.jaymoskingdoms.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Quicksand: sand soaked through, in bogs. It looks like the shore it lies in and holds nothing up. Whatever walks onto it
 * slows to a crawl and sinks a little every tick; jumping struggles upward (and tires a player), but only a little at a time,
 * and a body that sinks until its head is under begins to drown. It is how the lights of the bog ({@link BogLight}) take
 * travellers.
 */
public class QuicksandBlock extends Block {
	/** What is left of sideways motion, the sink a tick, the struggle's lift, and the slowest fall. */
	static final double CRAWL = 0.3, SINK = 0.035, STRUGGLE = 0.11, FASTEST_FALL = 0.06;
	/** The harm of being under, how often (ticks), and the tiring of a struggle (food exhaustion a tick). */
	static final float DROWN_HARM = 2.0F;
	static final int DROWN_EVERY = 20;
	static final float TIRING = 0.05F;

	public QuicksandBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean intersects) {
		if (entity.isSpectator() || entity.is(net.minecraft.tags.EntityTypeTags.FALL_DAMAGE_IMMUNE) && !(entity instanceof Player)) {
			return;
		}
		Vec3 v = entity.getDeltaMovement();
		double y = Math.max(v.y, -FASTEST_FALL) - SINK;
		if (entity instanceof ServerPlayer p && p.getLastClientInput().jump()) {
			y = STRUGGLE;
			((net.minecraft.world.entity.player.Player) p).causeFoodExhaustion(TIRING);
		}
		entity.setDeltaMovement(v.x * CRAWL, y, v.z * CRAWL);
		entity.syncVelocity = true;
		entity.resetFallDistance();
		if (level instanceof ServerLevel server && entity instanceof LivingEntity living && living.tickCount % DROWN_EVERY == 0) {
			if (server.getBlockState(BlockPos.containing(living.getEyePosition())).is(this)) {
				living.hurtServer(server, new DamageSource(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
					.getOrThrow(net.minecraft.world.damagesource.DamageTypes.DROWN)), DROWN_HARM);
				server.playSound(null, living.getX(), living.getEyeY(), living.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.BLOCKS, 0.6F, 0.6F);
			}
		}
	}

	@Override
	protected net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
		net.minecraft.world.phys.shapes.CollisionContext context) {
		return net.minecraft.world.phys.shapes.Shapes.empty();
	}

	@Override
	protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
		return false;
	}
}
