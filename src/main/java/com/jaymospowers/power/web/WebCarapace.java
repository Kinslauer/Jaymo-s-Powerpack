package com.jaymospowers.power.web;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Carapace: the black strain's Shroud tile (Andy, 2026-11-25: "this should be black's shroud instead"). The suit
 * hardens into plates for {@link #TICKS}: a share of every blow is soaked (absorption hearts and a Resistance cut), then
 * the plates soften, with a breath of ink, and the tile cools down (its cooldown runs from when it softens). The red
 * strain keeps the Shroud, the white one has {@link WebAegis}.
 */
public final class WebCarapace {
	/** How long it stays hard (ticks), the absorption it soaks (hit points), and the Resistance level (0 is a fifth off, 1 two fifths). */
	public static final int TICKS = 120;
	static final float SOAK = 8.0F;
	static final int CUT_LEVEL = 1;
	/** A plate's colour (a blue-black sheen). */
	static final int SHEEN = 0x1A1A2E;
	static final float SHAKE = 0.5F;

	/** When each hardened player softens (game time). */
	private static final Map<UUID, Long> UNTIL = new HashMap<>();

	private WebCarapace() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (UNTIL.isEmpty()) {
				return;
			}
			long now = server.overworld().getGameTime();
			UNTIL.entrySet().removeIf(e -> {
				ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
				if (player == null) {
					return true;
				}
				if (now < e.getValue() && player.isAlive()) {
					return false;
				}
				soften(player);
				return true;
			});
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> UNTIL.clear());
	}

	/** Whether {@code player}'s suit is hard now. */
	public static boolean hard(ServerPlayer player) {
		return UNTIL.containsKey(player.getUUID());
	}

	/** The Shroud tile for a black host: the suit hardens. */
	static boolean harden(ServerPlayer player) {
		ServerLevel level = player.level();
		UNTIL.put(player.getUUID(), level.getGameTime() + TICKS);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, TICKS, CUT_LEVEL, false, true));
		// (absorption tops up to the soak, never past it)
		if (player.getAbsorptionAmount() < SOAK) {
			player.setAbsorptionAmount(SOAK);
		}
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		MagicFx.blast(level, at, 1.6F, SHEEN);
		MagicFx.ring(level, player.position(), 1.4F, SHEEN);
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 24, 0.35, 0.7, 0.35, 0.03);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.PLAYERS, 1.1F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 0.5F);
		Shakes.send(player, SHAKE);
		return true;
	}

	/** The plates soften: what is left of the soak goes, the cut ends. */
	private static void soften(ServerPlayer player) {
		player.removeEffect(MobEffects.RESISTANCE);
		if (player.getAbsorptionAmount() > 0.0F) {
			player.setAbsorptionAmount(Math.max(0.0F, player.getAbsorptionAmount() - SOAK));
		}
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 14, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.8F, 0.7F);
		if (player.isAlive()) {
			WebSymbiote.whisper(player, "carapace.soft");
		}
	}
}
