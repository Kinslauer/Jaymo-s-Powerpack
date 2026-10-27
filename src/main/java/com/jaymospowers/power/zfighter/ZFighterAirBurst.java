package com.jaymospowers.power.zfighter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.42, Andy's NOEA picks) The air burst: double-tap forward while flying and a burst of ki throws him a few blocks
 * ahead. The client moves him ({@code ZFighterAirBurstClient}); this is the server's half: it takes the ki
 * ({@link #KI}), holds the {@link #COOLDOWN}, and gives the burst its flash and boom for everyone near. The barrel roll
 * (double-tap left or right) is the shared {@code FlightRoll}, which already covers a Z Fighter's flight.
 */
public final class ZFighterAirBurst {
	public static final float KI = 12.0F;
	static final int COOLDOWN = 24;
	static final float EXHAUSTION = 0.3F;

	private static final Map<UUID, Long> LAST = new HashMap<>();

	private ZFighterAirBurst() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
	}

	static void burst(ServerPlayer player) {
		ZFighterPower.Live live = ZFighterPower.live(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (!live.flying || last != null && now - last < COOLDOWN || !ZFighterPower.spend(player, live, KI)) {
			return;
		}
		LAST.put(player.getUUID(), now);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION);
		}
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		Vec3 back = player.getLookAngle().scale(-1.0);
		DustParticleOptions dust = ZFighterPower.dust(live);
		level.sendParticles(dust, true, false, at.x, at.y, at.z, 24, 0.25, 0.4, 0.25, 0.1);
		level.sendParticles(ParticleTypes.CLOUD, true, false, at.x, at.y, at.z, 0, back.x, back.y, back.z, 0.5);
		level.sendParticles(ParticleTypes.EXPLOSION, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.9F, 1.3F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4F, 1.8F);
	}
}
