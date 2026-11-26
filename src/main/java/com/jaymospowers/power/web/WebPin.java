package com.jaymospowers.power.web;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Pin to the wall: Sneak + Yank (B) on a creature yanks it sideways into the nearest wall (within {@link #WALL_REACH}), where
 * it sticks for {@link #PIN_TICKS} (a pin of the impact web's kind, {@link WebStrength#pinAt}: it can't walk and takes
 * {@link WebStrength#PINNED_DAMAGE} times the damage). With no wall near it is whipped down and stuck to the ground instead.
 * Only what the tendrils or web could lift ({@link WebHaul#haulable}), and never a pet of yours or a player you may not hurt.
 */
public final class WebPin {
	/** How far from the foe a wall counts (blocks), and how long the pin holds (ticks). */
	public static final double WALL_REACH = 7.0;
	public static final int PIN_TICKS = 80;
	/** The directions looked in, round the compass. */
	private static final int DIRECTIONS = 12;
	/** Damage of the slap into the wall (or the floor). */
	static final float SLAP_DAMAGE = 4.0F;

	private WebPin() {
	}

	/** Whether Sneak + Yank can pin {@code target}. */
	static boolean pinnable(ServerPlayer player, LivingEntity target) {
		return target.isAlive() && target != player && !target.isSpectator() && WebHaul.haulable(target)
			&& !(target instanceof TamableAnimal pet && pet.isOwnedBy(player)) && !(target instanceof Player other && !player.canHarmPlayer(other));
	}

	/** The pin. False if {@code target} can't be pinned (the Yank goes on as it always did). */
	static boolean pin(ServerPlayer player, LivingEntity target) {
		if (!pinnable(player, target)) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 from = target.getBoundingBox().getCenter();
		double half = target.getBbWidth() * 0.5;
		double best = Double.MAX_VALUE;
		Vec3 wall = null;
		Vec3 wallDir = null;
		for (int i = 0; i < DIRECTIONS; i++) {
			double a = i * Math.PI * 2.0 / DIRECTIONS;
			Vec3 dir = new Vec3(Math.cos(a), 0.0, Math.sin(a));
			BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(WALL_REACH + half)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
			if (hit.getType() == HitResult.Type.BLOCK) {
				double d = hit.getLocation().distanceTo(from);
				if (d < best) {
					best = d;
					wall = hit.getLocation();
					wallDir = dir;
				}
			}
		}
		Vec3 at;
		if (wall != null) {
			at = wall.subtract(wallDir.scale(half + 0.05));
			at = new Vec3(at.x, target.getY(), at.z);
		} else {
			at = WebHaul.ground(level, target.position(), target);
		}
		Vec3 old = target.position();
		WebPower.pose(player, WebPose.PIN_THROW, player.getRandom().nextBoolean() ? 1 : -1);
		WebStrikeLines.toEntity(player, target);
		target.setInvulnerableTime(0);
		target.hurtServer(level, player.damageSources().mobAttack(player), SLAP_DAMAGE);
		if (!target.isAlive()) {
			return true;
		}
		WebStrength.pinAt(level, target, at, PIN_TICKS, WebSymbiote.suitShown(player));
		// the web it flew along
		Vec3 now = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		Vec3 start = old.add(0.0, target.getBbHeight() * 0.5, 0.0);
		for (int i = 0; i <= 8; i++) {
			Vec3 p = start.lerp(now, i / 8.0);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		level.sendParticles(ParticleTypes.ITEM_COBWEB, now.x, now.y, now.z, 18, target.getBbWidth() * 0.5, target.getBbHeight() * 0.4, target.getBbWidth() * 0.5, 0.05);
		level.playSound(null, now.x, now.y, now.z, SoundEvents.SLIME_BLOCK_HIT, SoundSource.PLAYERS, 1.2F, 0.7F);
		com.jaymospowers.core.Shakes.send(player, 0.4F, now);
		return true;
	}
}
