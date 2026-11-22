package com.jaymospowers.stamina;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Stop, drop and roll (Andy: "we also stop drop and roll to put fire out"). A burning player who holds Sneak on the
 * ground for {@link #DROP_TICKS} drops and rolls side to side where they lie, drawn as the dodge's tumble
 * ({@link Dodge#ROLLING}, seen by everyone), for no feathers. Each roll smothers {@link #FIRE_PER_ROLL} of the fire, and
 * {@link #ROLLS_OUT} rolls put it out whatever is left. Standing in fire or lava keeps relighting you, as it should.
 */
public final class FireRoll {
	/** Ticks of holding Sneak on the ground, burning, before you drop. */
	static final int DROP_TICKS = 6;
	/** Fire ticks each roll smothers, and the rolls that put it out entirely. */
	static final int FIRE_PER_ROLL = 60, ROLLS_OUT = 3;

	private static final class Rolling {
		int held, rolls;
		long lastRoll = Long.MIN_VALUE / 2;
	}

	private static final Map<UUID, Rolling> ROLLERS = new HashMap<>();

	private FireRoll() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(FireRoll::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ROLLERS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ROLLERS.clear());
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean burning = player.isOnFire() && !player.fireImmune() && !player.isSpectator();
			if (!burning || !player.isShiftKeyDown() || !player.onGround() || player.isPassenger() || player.isInLava()) {
				ROLLERS.remove(player.getUUID());
				continue;
			}
			Rolling rolling = ROLLERS.computeIfAbsent(player.getUUID(), id -> new Rolling());
			if (++rolling.held < DROP_TICKS) {
				continue;
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			if (now - rolling.lastRoll < Dodge.ROLL_TICKS) {
				continue;
			}
			// one tumble to the side, then back the other way
			rolling.lastRoll = now;
			rolling.rolls++;
			Vec3 side = Vec3.directionFromRotation(0.0F, player.getYRot() + (rolling.rolls % 2 == 0 ? 90.0F : -90.0F));
			player.setAttached(Dodge.ROLLING, new Dodge.Rolling(now, (float) side.x, (float) side.z, false));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.7F, 0.7F);
			level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.3, player.getZ(), 8, 0.4, 0.15, 0.4, 0.01);
			if (rolling.rolls >= ROLLS_OUT || player.getRemainingFireTicks() <= FIRE_PER_ROLL) {
				player.clearFire();
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.3F);
				ROLLERS.remove(player.getUUID());
			} else {
				player.setRemainingFireTicks(player.getRemainingFireTicks() - FIRE_PER_ROLL);
			}
		}
	}
}
