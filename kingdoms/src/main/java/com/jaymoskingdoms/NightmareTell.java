package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Andy (clip "invulnerable nightmare"): "is the nightmare supposed to be invulnerable?" The Graveyard's Nightmare takes
 * no harm from anything shot or thrown (an arrow, a bolt, WeaponMod's musket and flintlock balls), only from a blow in
 * hand, which is the mod's design. It never showed: the shot simply did nothing. Now a shot goes through it as a puff
 * of smoke with a soul's sigh, and the one who fired is told, now and then, that only steel up close will do
 * ({@code mixin.graveyard.NightmareShotMixin}; skipped without The Graveyard).
 */
public final class NightmareTell {
	/** The shooter is told again no sooner than this (ticks). */
	static final int TELL_TICKS = 20 * 10;

	/** Shooter → when they were last told (game time). */
	private static final Map<UUID, Long> TOLD = new HashMap<>();

	private NightmareTell() {
	}

	/** {@code nightmare} is struck by {@code source}: a shot goes through it, and its shooter is told why. */
	public static void struck(LivingEntity nightmare, ServerLevel level, DamageSource source) {
		if (!source.is(DamageTypeTags.IS_PROJECTILE)) {
			return;
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, nightmare.getX(), nightmare.getY(0.6), nightmare.getZ(), 8, 0.2, 0.5, 0.2, 0.01);
		level.playSound(null, nightmare.getX(), nightmare.getY(), nightmare.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.8F, 0.6F);
		if (source.getEntity() instanceof ServerPlayer player) {
			long now = level.getGameTime();
			Long last = TOLD.get(player.getUUID());
			if (last == null || now - last >= TELL_TICKS || now < last) {
				TOLD.put(player.getUUID(), now);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.nightmare.shot").withStyle(ChatFormatting.GRAY));
			}
		}
	}
}
