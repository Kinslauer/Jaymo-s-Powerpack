package com.jaymospowers.aether;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Green aercloud: a cold cloud that shoves whatever enters it a block a tick in a random horizontal direction. Each
 * entity can be shoved again only after {@link #COOLDOWN} ticks. Runs on both sides (the player's own client throws the
 * player; the server throws everything else).
 */
public class GreenAercloudBlock extends AercloudBlock {
	// ---- tuning ----
	/** Sideways speed (blocks a tick) of the shove, and ticks before the same entity can be shoved again. */
	public static final double SHOVE_SPEED = 1.0;
	public static final int COOLDOWN = 20;

	/** The game time of each entity's last shove (kept per side; entities are not the same object on both). */
	private static final Map<Entity, Long> LAST = Collections.synchronizedMap(new WeakHashMap<>());

	public GreenAercloudBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		this.settle(entity);
		long now = level.getGameTime();
		Long last = LAST.get(entity);
		if (entity instanceof Projectile || (last != null && now - last < COOLDOWN && now >= last)) {
			return;
		}
		LAST.put(entity, now);
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		Vec3 v = entity.getDeltaMovement();
		entity.setDeltaMovement(Math.cos(angle) * SHOVE_SPEED, Math.max(v.y, 0.1), Math.sin(angle) * SHOVE_SPEED);
		entity.setOnGround(false);
		if (level.isClientSide()) {
			for (int i = 0; i < 8; i++) {
				level.addParticle(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.5, entity.getZ(),
					Math.cos(angle) * 0.2, 0.05, Math.sin(angle) * 0.2);
			}
		}
	}
}
