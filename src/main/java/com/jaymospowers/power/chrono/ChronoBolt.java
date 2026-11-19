package com.jaymospowers.power.chrono;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The speed's lightning, thrown: in top gear ({@link ChronoGears#GEARS}) with an empty hand, use (right click) with no
 * static charge to hurl and nothing to catch or snatch throws a jagged bolt down your sight, in your Speed Prism's colour
 * ({@link SpeedPrism#colour}), that bursts where it lands: damage and a small knock to everything within
 * {@link #BURST_RADIUS}, a flash and a crack of thunder. It never lights a fire or breaks a block. Every
 * {@link #COOLDOWN} ticks at most, and it costs {@link #EXHAUSTION} hunger. Velox's Velocity throws it too, in its blue.
 */
final class ChronoBolt {
	// ---- tuning ----
	/** How far the bolt reaches (blocks), and how far it is jagged off line at most (blocks). */
	static final double RANGE = 36.0, JAG = 0.5;
	/** Segments of the jagged line, and the burst's reach (blocks), damage and knock (blocks a tick). */
	static final int SEGMENTS = 9;
	static final double BURST_RADIUS = 3.0, KNOCK = 0.7;
	static final float DAMAGE = 7.0F;
	/** Ticks between bolts, and the hunger each costs. */
	static final int COOLDOWN = 30;
	static final float EXHAUSTION = 1.5F;
	private static final String TABLE = "chrono_bolt";
	private static final int SLOT = 0;

	private ChronoBolt() {
	}

	/** Throws a bolt if {@code player} is in top gear with an empty hand and it is off cooldown; true if one went. */
	static boolean throwBolt(ServerPlayer player, ServerLevel level) {
		if (ChronoGears.gear(player) < ChronoGears.GEARS || !player.getMainHandItem().isEmpty() || !PickKit.ready(TABLE, player, SLOT)) {
			return false;
		}
		PickKit.spend(TABLE, player, SLOT, COOLDOWN);
		player.causeFoodExhaustion(EXHAUSTION);
		int colour = SpeedPrism.colour(player, ChronoSpeed.velocity(player) ? ChronoMomentum.SKY : ChronoMomentum.GOLD);
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(RANGE));
		HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		double near = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(eye, end).inflate(1.0), e -> PickKit.foe(player, e))) {
			var hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
			if (hit.isPresent() && hit.get().distanceToSqr(eye) < near) {
				near = hit.get().distanceToSqr(eye);
				end = hit.get();
			}
		}
		Vec3 from = eye.add(look.scale(0.6)).add(0.0, -0.3, 0.0);
		jagged(level, from, end, colour);
		burst(player, level, end, colour);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
		return true;
	}

	/** A lightning line from {@code from} to {@code to}: strokes between points pushed a little off the straight line. */
	private static void jagged(ServerLevel level, Vec3 from, Vec3 to, int colour) {
		RandomSource random = level.getRandom();
		Vec3 line = to.subtract(from);
		Vec3 last = from;
		for (int i = 1; i <= SEGMENTS; i++) {
			Vec3 next = from.add(line.scale(i / (double)SEGMENTS));
			if (i < SEGMENTS) {
				next = next.add((random.nextDouble() - 0.5) * 2.0 * JAG, (random.nextDouble() - 0.5) * 2.0 * JAG, (random.nextDouble() - 0.5) * 2.0 * JAG);
			}
			MagicFx.stroke(level, last, next, 0.12F, colour);
			last = next;
		}
		MagicFx.beam(level, from, to, 0.05F, SpeedPrism.whiten(colour, SpeedPrism.CORE_WHITEN), 3);
	}

	/** The burst where it landed: damage and a knock out from the middle to every foe in reach; no fire, no blocks. */
	private static void burst(ServerPlayer player, ServerLevel level, Vec3 at, int colour) {
		MagicFx.blast(level, at, (float)BURST_RADIUS, colour);
		MagicFx.ring(level, at, (float)BURST_RADIUS, colour);
		level.sendParticles(MagicFx.spark(colour, 1.0F), at.x, at.y, at.z, 12, 0.5, 0.5, 0.5, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.7F);
		Shakes.around(level, at, 14.0, 0.5F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, PickKit.around(at, BURST_RADIUS), c -> PickKit.foe(player, c))) {
			if (e.getBoundingBox().getCenter().distanceTo(at) > BURST_RADIUS + e.getBbWidth() * 0.5) {
				continue;
			}
			if (e.hurtServer(level, level.damageSources().indirectMagic(player, player), DAMAGE)) {
				Vec3 out = e.getBoundingBox().getCenter().subtract(at).multiply(1.0, 0.0, 1.0);
				Vec3 push = (out.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(1.0, 0.0, 1.0) : out).normalize().scale(KNOCK).add(0.0, 0.2, 0.0);
				e.setDeltaMovement(e.getDeltaMovement().add(push));
				e.syncVelocity = true;
			}
		}
	}
}
