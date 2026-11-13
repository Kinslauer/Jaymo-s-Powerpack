package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Cover that counts (fifth edition's half and three-quarters cover, Andy's pick C3). Crouch behind a fence, a low wall, a
 * hay bale or any block that comes up to your chest, and arrows, bolts and spell bolts coming at you hit the cover more
 * often and stick in it, instead of you. How much it covers is read off the body itself: lines are cast from a few feet
 * back along the shot to the legs, the belly and the chest of whoever it is about to hit, and the share the cover stands
 * across is the chance the shot is stopped. A crouching body is lower, so a fence covers more of it; a standing one has
 * only what a wall of its own height covers (a half of it, at most). Cover from above does nothing, and a shield is the
 * shield's own business.
 * <p>
 * Hooked at the projectile's moment of hitting ({@code mixin.fifth.ProjectileCoverMixin}); the shooters it is for are
 * arrows and bolts and whatever is in {@code #jaymoskingdoms:cover_stops} (harpoons, bullets, thrown lances and fire).
 * A Sorcerer's bolt and a court wizard's are lances of light with nothing in flight, so they ask {@link #takesBolt} themselves. The NPC
 * shooters look for cover and lean out to shoot through {@link RangedTactics}, which asks {@link #spot} for a place.
 */
public final class Cover {
	/** The lines are cast from this far back along the shot (blocks); cover further than that is not about this body. */
	static final double BACK = 3.0;
	/** Heights of the lines up the body, as shares of its height: legs, belly, chest. */
	static final double[] LINES = {0.25, 0.55, 0.8};
	/** Standing behind cover is worth this share of what crouching is (a fence covers the legs and belly, not the head). */
	static final double STANDING = 0.6;
	/** The most a lone block of cover stops of a shot. */
	static final double MOST = 0.85;

	public static final TagKey<EntityType<?>> STOPS = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("cover_stops"));

	private Cover() {
	}

	/** Whether this projectile is the kind that cover stops: arrows and bolts, and what the tag lists. */
	public static boolean stoppable(Projectile projectile) {
		return projectile instanceof AbstractArrow || projectile.is(STOPS);
	}

	/**
	 * A shot about to hit {@code target}: the block it sticks in if cover takes it (rolled against how much of the body the
	 * cover stands across), or null if it hits the body.
	 */
	public static @Nullable BlockHitResult takes(ServerLevel level, Projectile projectile, LivingEntity target) {
		if (!stoppable(projectile) || projectile.getOwner() == null || projectile.getOwner() == target || target.isSpectator()) {
			return null;
		}
		Vec3 dir = projectile.getDeltaMovement();
		if (dir.lengthSqr() < 1.0E-4) {
			return null;
		}
		return roll(level, projectile.position().subtract(dir.normalize().scale(BACK)), projectile, target);
	}

	/**
	 * A spell bolt from {@code caster} about to strike {@code target} (the bolts here are lances of light, not things in
	 * flight): the block it bursts on if cover takes it, or null if it strikes the body. Read the same way as a shot's.
	 */
	public static @Nullable BlockHitResult takesBolt(ServerLevel level, LivingEntity caster, LivingEntity target) {
		if (caster == target || target.isSpectator()) {
			return null;
		}
		Vec3 centre = target.getBoundingBox().getCenter();
		Vec3 dir = centre.subtract(caster.getEyePosition());
		if (dir.lengthSqr() < 1.0) {
			return null;
		}
		Vec3 back = dir.lengthSqr() <= BACK * BACK ? caster.getEyePosition() : centre.subtract(dir.normalize().scale(BACK));
		return roll(level, back, caster, target);
	}

	/** Lines from {@code back} to the legs, belly and chest of {@code target}: the first block in the way, if the roll says the cover took it. */
	private static @Nullable BlockHitResult roll(ServerLevel level, Vec3 back, net.minecraft.world.entity.Entity from, LivingEntity target) {
		BlockHitResult best = null;
		int covered = 0;
		for (double line : LINES) {
			Vec3 body = new Vec3(target.getX(), target.getY() + target.getBbHeight() * line, target.getZ());
			BlockHitResult hit = level.clip(new ClipContext(back, body, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, from));
			if (hit.getType() == HitResult.Type.BLOCK) {
				covered++;
				if (best == null) {
					best = hit;
				}
			}
		}
		if (best == null) {
			return null;
		}
		double share = covered / (double) LINES.length;
		boolean low = target.isCrouching() || target instanceof net.minecraft.world.entity.player.Player p && PowersCompat.lowStance(p);
		double chance = Math.min(MOST, low ? share : share * STANDING);
		return level.getRandom().nextDouble() < chance ? best : null;
	}

	/** The shot sticks in the cover: a dull thud and a puff of the block's dust at the spot. */
	public static void stuck(ServerLevel level, BlockHitResult hit, LivingEntity target) {
		Vec3 at = hit.getLocation();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ARROW_HIT, SoundSource.BLOCKS, 0.8F, 0.8F);
		level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(hit.getBlockPos())), at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.05);
	}

	/**
	 * A place for a shooter to take cover from {@code foe}: a spot within {@code reach} blocks of it where, crouching, the line to
	 * the foe is stopped by something, and standing it is clear (so it can lean out to shoot). Null if none is found.
	 */
	public static @Nullable BlockPos spot(ServerLevel level, LivingEntity shooter, LivingEntity foe, double reach, int tries) {
		var random = shooter.getRandom();
		double crouch = shooter.getBbHeight() * 0.72;
		for (int i = 0; i < tries; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0, d = 2.0 + random.nextDouble() * (reach - 2.0);
			BlockPos column = BlockPos.containing(shooter.getX() + Math.cos(angle) * d, shooter.getY(), shooter.getZ() + Math.sin(angle) * d);
			BlockPos feet = null;
			for (int dy : new int[] {0, 1, -1}) {
				BlockPos p = column.offset(0, dy, 0);
				if (level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
					&& !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty() && level.getFluidState(p).isEmpty()) {
					feet = p;
					break;
				}
			}
			if (feet == null) {
				continue;
			}
			Vec3 base = Vec3.atBottomCenterOf(feet);
			Vec3 foeEye = foe.getEyePosition();
			Vec3 standing = base.add(0.0, shooter.getEyeHeight(), 0.0), ducking = base.add(0.0, crouch, 0.0);
			boolean leans = level.clip(new ClipContext(standing, foeEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter)).getType() == HitResult.Type.MISS;
			boolean hidden = level.clip(new ClipContext(ducking, foe.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter)).getType() == HitResult.Type.BLOCK;
			if (leans && hidden && base.distanceTo(foe.position()) > 5.0) {
				return feet;
			}
		}
		return null;
	}
}
