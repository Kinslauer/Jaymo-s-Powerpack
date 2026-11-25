package com.jaymospowers.power.web;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Aegis: the white strain's Shroud tile (Andy, 2026-11-25: "replaces shroud"), from the Integrated stage
 * ({@code WebSymbioteMind.STAGE_AT[1]}). The suit sets into a pale hard shell for {@link #TICKS}: it cuts blows a
 * little (Resistance) and throws {@link #REFLECT} of each one back at whoever struck, up to {@link #REFLECT_MAX} a blow.
 * It never reflects what it was not struck by (fire, falls) and never a reflected blow again. The cooldown runs from
 * when it lets go.
 */
public final class WebAegis {
	/** How long the shell holds (ticks), the Resistance level under it, and the share of a blow sent back (and its cap, hit points). */
	public static final int TICKS = 140;
	static final int CUT_LEVEL = 0;
	static final float REFLECT = 0.5F, REFLECT_MAX = 8.0F;
	/** The shell's colour (bone white). */
	static final int PALE = 0xF2F0E4;
	static final float SHAKE = 0.5F, RETURN_SHAKE = 0.35F;

	private static final Map<UUID, Long> UNTIL = new HashMap<>();

	private WebAegis() {
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
				player.removeEffect(MobEffects.RESISTANCE);
				if (player.isAlive()) {
					Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
					player.level().sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 0.3, 0.6, 0.3, 0.02);
					player.level().playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
					WebSymbiote.whisper(player, "aegis.soft");
				}
				return true;
			});
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity instanceof ServerPlayer player) || !UNTIL.containsKey(player.getUUID()) || source.is(DamageTypeTags.IS_FIRE)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.THORNS) || !(source.getEntity() instanceof LivingEntity striker)
				|| striker == player || !striker.isAlive()) {
				return;
			}
			float back = Math.min(REFLECT_MAX, baseDamage * REFLECT);
			if (back <= 0.0F || !(player.level() instanceof ServerLevel level)) {
				return;
			}
			striker.hurtServer(level, level.damageSources().thorns(player), back);
			Vec3 at = striker.getBoundingBox().getCenter();
			MagicFx.blast(level, at, 0.9F, PALE);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.08);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.3F);
			Shakes.send(player, RETURN_SHAKE, striker.position());
			WebSymbiote.narrate(player, "aegis_return");
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> UNTIL.clear());
	}

	/** Whether {@code player}'s shell is up. */
	public static boolean up(ServerPlayer player) {
		return UNTIL.containsKey(player.getUUID());
	}

	/** The Shroud tile for a white host, from the Integrated stage: the shell sets. False (and it says so) before then. */
	static boolean raise(ServerPlayer player) {
		if (WebSymbiote.state(player).bond() < WebSymbioteMind.STAGE_AT[1]) {
			WebSymbiote.nag(player, "aegis_locked");
			return false;
		}
		ServerLevel level = player.level();
		UNTIL.put(player.getUUID(), level.getGameTime() + TICKS);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, TICKS, CUT_LEVEL, false, true));
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		MagicFx.blast(level, at, 1.7F, PALE);
		MagicFx.ring(level, player.position(), 1.5F, PALE);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 20, 0.4, 0.7, 0.4, 0.03);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.1F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 0.6F);
		Shakes.send(player, SHAKE);
		return true;
	}
}
