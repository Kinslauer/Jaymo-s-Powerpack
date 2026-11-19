package com.jaymospowers.power.wind;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Storm landing: press the key (on the ground or in the air), then jump. On the ground the jump is a great leap; in the
 * air it is a dive. When you land, lightning strikes the ground round you, more bolts and harder the further you fell.
 * The fall itself still hurts (fall damage always applies, see {@link StormNet#hardFall}).
 */
final class StormLanding {
	private static final String TABLE = "wind_storm";
	private static final int SLOT = 5;
	/** How long the key stays armed waiting for the jump (ticks), the cooldown after a landing, hunger. */
	static final int ARMED_TICKS = 200, COOLDOWN = 160;
	static final float EXHAUSTION = 2.0F;
	static final int MIN_FOOD = 6;
	/** The leap off the ground, the dive in the air (blocks/tick). */
	static final double LEAP = 1.0, DIVE = 2.0;
	/** A landing needs at least this much drop (blocks) to call lightning; bolts: base, per this many blocks, most. */
	static final double MIN_DROP = 3.0, DROP_PER_BOLT = 3.0;
	static final int BOLTS_BASE = 2, BOLTS_MAX = 8;
	/** Each bolt's damage: base plus per block of drop, at most; how far from you they fall (base, per block of drop, most). */
	static final float DAMAGE_BASE = 3.0F, DAMAGE_PER_BLOCK = 0.5F, DAMAGE_MAX = 14.0F;
	static final double REACH_BASE = 2.5, REACH_PER_BLOCK = 0.15, REACH_MAX = 7.0, BOLT_REACH = 2.2;
	/** Falls hurt in full this long after the jump and after landing (ticks). */
	static final int HARD_FALL_TICKS = 300, HARD_FALL_AFTER = 20;

	private static final class Landing {
		long armedUntil;
		boolean airborne;
		boolean jumped;
		double peak;
		long leftAt;
	}

	private static final Map<UUID, Landing> LANDINGS = new HashMap<>();

	private StormLanding() {
	}

	static boolean armed(ServerPlayer player) {
		Landing l = LANDINGS.get(player.getUUID());
		return l != null && (l.airborne || l.armedUntil > player.level().getGameTime());
	}

	static void press(ServerPlayer player) {
		if (!PickKit.ready(TABLE, player, SLOT)) {
			StormNet.say(player, "cooling");
			return;
		}
		if (!player.isCreative() && player.getFoodData().getFoodLevel() < MIN_FOOD) {
			StormNet.say(player, "hungry");
			return;
		}
		Landing l = LANDINGS.computeIfAbsent(player.getUUID(), k -> new Landing());
		if (l.airborne) {
			return;
		}
		l.armedUntil = player.level().getGameTime() + ARMED_TICKS;
		l.jumped = player.getLastClientInput().jump();
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COPPER_BULB_TURN_ON, SoundSource.PLAYERS, 0.8F, 1.5F);
		level.sendParticles(MagicFx.spark(StormDraw.BOLT, 0.8F), player.getX(), player.getY() + 0.2, player.getZ(), 6, 0.4, 0.1, 0.4, 0.0);
	}

	static void tick(ServerPlayer player) {
		Landing l = LANDINGS.get(player.getUUID());
		if (l == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		boolean jump = player.getLastClientInput().jump();
		boolean tapped = jump && !l.jumped;
		l.jumped = jump;
		if (!l.airborne) {
			if (l.armedUntil <= now) {
				LANDINGS.remove(player.getUUID());
				return;
			}
			if (tapped) {
				Vec3 v = player.getDeltaMovement();
				if (player.onGround()) {
					player.setDeltaMovement(v.x, LEAP, v.z);
				} else {
					player.setDeltaMovement(0.0, -DIVE, 0.0);
				}
				player.syncVelocity = true;
				player.resetFallDistance();
				l.airborne = true;
				l.peak = player.getY();
				l.leftAt = now;
				StormNet.hardFall(player, HARD_FALL_TICKS);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 0.8F);
			}
			return;
		}
		l.peak = Math.max(l.peak, player.getY());
		level.sendParticles(MagicFx.spark(StormDraw.BOLT, 0.8F), player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.6, 0.3, 0.0);
		if (now - l.leftAt < 3 || !(player.onGround() || player.isInWater())) {
			if (now - l.leftAt > 400) {
				LANDINGS.remove(player.getUUID());
			}
			return;
		}
		double drop = l.peak - player.getY();
		LANDINGS.remove(player.getUUID());
		StormNet.hardFall(player, HARD_FALL_AFTER);
		PickKit.spend(TABLE, player, SLOT, COOLDOWN);
		if (drop < MIN_DROP) {
			return;
		}
		land(player, level, drop);
	}

	/** Lightning round the landing, scaled by the drop. */
	private static void land(ServerPlayer player, ServerLevel level, double drop) {
		player.causeFoodExhaustion(EXHAUSTION);
		int bolts = (int)Math.min(BOLTS_MAX, BOLTS_BASE + drop / DROP_PER_BOLT);
		float damage = (float)Math.min(DAMAGE_MAX, DAMAGE_BASE + drop * DAMAGE_PER_BLOCK);
		double reach = Math.min(REACH_MAX, REACH_BASE + drop * REACH_PER_BLOCK);
		Vec3 at = player.position();
		var random = player.getRandom();
		double offset = random.nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < bolts; i++) {
			double a = offset + i * Math.PI * 2.0 / bolts;
			double r = reach * (0.7 + random.nextDouble() * 0.3);
			WindBolts.strike(player, new Vec3(at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r), damage, BOLT_REACH);
		}
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), (float)reach, StormDraw.BOLT);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.4F, 0.9F);
	}

	static void forget(ServerPlayer player) {
		LANDINGS.remove(player.getUUID());
	}
}
