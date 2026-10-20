package com.jaymospowers.power.smoke;

import com.jaymospowers.core.Powers;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Sneak + X, Smoke Step: you come apart in a puff and re-form inside one of your own clouds under the crosshair, up to
 * {@link #RANGE} blocks away, through walls. The first block along your look holding at least {@link #THICK} of your
 * smoke is where you go (somewhere in it, or just above or below, where you fit). Cooldown {@link #COOLDOWN}
 * ({@link #COOLDOWN_MASTERED} with everything unlocked).
 */
public final class SmokeStep {
	static final double RANGE = 32.0;
	/** How thick your smoke must be to step into (a block's worth is 1). */
	static final float THICK = 1.0F;
	static final int COOLDOWN = 120, COOLDOWN_MASTERED = 80;
	/** Smoke left where you stood and where you come out. */
	static final float PUFF_FROM = 3.0F, PUFF_TO = 1.5F;

	private SmokeStep() {
	}

	static void step(ServerPlayer player) {
		if (!Powers.has(player, SmokePower.INSTANCE) || !player.isAlive() || player.isSpectator() || SmokeVapour.wholly(player)) {
			return;
		}
		ServerLevel level = player.level();
		long left = SmokePower.state(player).left(SmokeState.STEP, level.getGameTime());
		if (left > 0) {
			say(player, Component.translatable("message.jaymospowers.smoke_step_wait", (left + 19) / 20));
			return;
		}
		Vec3 to = find(level, player);
		if (to == null) {
			say(player, Component.translatable("message.jaymospowers.smoke_step_none"));
			return;
		}
		Vec3 from = player.position();
		Vec3 mid = from.add(0.0, player.getBbHeight() * 0.5, 0.0);
		SmokeField.puff(level, mid, PUFF_FROM, player);
		level.sendParticles(SmokeParticles.burst(player, ParticleTypes.LARGE_SMOKE), mid.x, mid.y, mid.z, 30, 0.3, 0.6, 0.3, 0.03);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.7F);
		player.teleportTo(level, to.x, to.y, to.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		SmokeMoves.begin(player, SmokeMoves.STEP);
		Vec3 out = to.add(0.0, player.getBbHeight() * 0.5, 0.0);
		SmokeField.puff(level, out, PUFF_TO, player);
		level.sendParticles(SmokeParticles.burst(player, ParticleTypes.LARGE_SMOKE), out.x, out.y, out.z, 24, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, out.x, out.y, out.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.6F, 0.7F);
		SmokePower.cooldown(player, SmokeState.STEP, SmokePower.mastered(player) ? COOLDOWN_MASTERED : COOLDOWN);
	}

	/** Where to come out: feet position inside the first thick cloud of yours along the look, or null. */
	private static @Nullable Vec3 find(ServerLevel level, ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		long last = Long.MIN_VALUE;
		for (double t = 1.0; t <= RANGE; t += 0.5) {
			BlockPos pos = BlockPos.containing(eye.add(look.scale(t)));
			if (pos.asLong() == last) {
				continue;
			}
			last = pos.asLong();
			if (SmokeField.density(level, pos) < THICK || SmokeField.owner(level, pos) != player.getId()) {
				continue;
			}
			// stand in it, or with your head in it, or on top of it
			for (int dy : new int[] {0, -1, 1, -2}) {
				Vec3 feet = Vec3.atBottomCenterOf(pos.above(dy));
				if (level.noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())))) {
					return feet;
				}
			}
		}
		return null;
	}

	private static void say(ServerPlayer player, Component message) {
		player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.GRAY));
	}
}
